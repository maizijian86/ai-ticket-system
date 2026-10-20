package com.aiticket.user.service;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.common.enums.Role;
import com.aiticket.common.exception.BusinessException;
import com.aiticket.common.storage.OssStorage;
import com.aiticket.common.util.JwtUtil;
import com.aiticket.user.dto.*;
import com.aiticket.user.entity.User;
import com.aiticket.user.entity.UserSkill;
import com.aiticket.user.repository.UserRepository;
import com.aiticket.user.repository.UserSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final OssStorage ossStorage;

    // ==================== 头像 ====================

    /**
     * 默认头像的取值前缀，完整形态是 {@code default:1} ~ {@code default:9}。
     *
     * <p>对应的图是前端 fixture {@code frontend/public/avatars/{n}.svg}（9 个，来自
     * 仓库根目录的 icon_tw0u8p192hc/）。**刻意不把这些图传到 OSS**：9 张永不改变的图
     * 放 OSS 要付存储和每次请求的流量，而它们跟代码一样是版本化的资产，
     * 放 public/ 里跟着前端一起发版才对。
     *
     * <p>改这个数字要同时改：前端那个 fixture 目录、以及 {@code db/migration.sql} 里
     * 存量用户回填用的 {@code RAND() * 9}。三处不同步就会出现"回填出一个前端没有的 N"。
     */
    public static final String DEFAULT_AVATAR_PREFIX = "default:";
    public static final int DEFAULT_AVATAR_COUNT = 9;

    /** 头像单文件上限。前端会先压到 200×200，正常只有几十 KB —— 这是兜底。 */
    private static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;

    /**
     * 允许的头像 MIME。白名单而不是黑名单：黑名单永远漏，而这里多拦一种格式的代价
     * 只是用户换个格式，放进来一个可执行类型的代价则大得多。
     */
    private static final Set<String> AVATAR_ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /**
     * 自助注册时**允许**客户端指定的身份。
     *
     * <p>此前这里是裸枚举、原样落库 —— 任何人都能往 /auth/register 里塞
     * role=ADMIN 把自己注册成管理员。注册接口是 permitAll 的，所以那是一个
     * 从外部就能用的提权入口，不是理论问题。
     *
     * <p>ADMIN 不在白名单里：管理员只能由已有管理员在 /users 里改出来
     * （UserService.updateUser 仍接受任意 Role，那是管理端内部路径）。
     * USER 也不在白名单里 —— 它是存量兼容身份，新账号不该再落到这个"两边都能"
     * 的模糊值上；null 仍然映射到它只是为了不破坏已有调用方。
     */
    private static final Set<Role> SELF_REGISTERABLE_ROLES = Set.of(Role.PUBLISHER, Role.HANDLER);

    @Transactional
    public UserDTO register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(400, "Username already exists");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(400, "Email already exists");
        }

        Role role = request.getRole();
        if (role != null && !SELF_REGISTERABLE_ROLES.contains(role)) {
            throw new BusinessException(400, "该身份不允许自助注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        // 注册时随机发一个默认头像。发在这儿而不是"读的时候兜底"，是因为头像一旦定下
        // 就不该再变 —— 若在 toDTO 里对 NULL 现算，同一个用户每次查出来的头像都不一样。
        user.setAvatarUrl(randomDefaultAvatar());
        user.setRole(role != null ? role : Role.USER);
        user.setStatus("active");
        user.setGithubUsername(request.getGithubUsername());
        user.setGithubRepos(request.getGithubRepos());

        user = userRepository.save(user);

        // Create default skill profile
        UserSkill skill = new UserSkill();
        skill.setUserId(user.getId());
        skill.setUserName(user.getNickname());
        skill.setSkillTags(new String[]{});
        skill.setCurrentLoad(0);
        skill.setMaxLoad(10);
        userSkillRepository.save(skill);

        log.info("User registered: {}", user.getUsername());
        return toDTO(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(401, "Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "Invalid username or password");
        }

        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(403, "User account is inactive");
        }

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 生成双Token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 保存Refresh Token到Redis
        refreshTokenService.saveRefreshToken(user.getId(), refreshToken);

        log.info("User logged in: {}", user.getUsername());

        return new LoginResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getNickname(),
                displayAvatar(user.getAvatarUrl())
        );
    }

    /**
     * 刷新Token
     */
    public LoginResponse refreshToken(Long userId, String refreshToken) {
        // 验证Refresh Token
        if (!refreshTokenService.validateRefreshToken(userId, refreshToken)) {
            throw new BusinessException(401, "Invalid or expired refresh token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "User not found"));

        // 生成新的Access Token
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        log.info("Token refreshed for user: {}", user.getUsername());

        return new LoginResponse(
                newAccessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getNickname(),
                displayAvatar(user.getAvatarUrl())
        );
    }

    /**
     * 用户登出
     */
    public void logout(Long userId) {
        refreshTokenService.deleteRefreshToken(userId);
        log.info("User logged out: userId={}", userId);
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "User not found"));
        return toDTO(user);
    }

    public UserDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "User not found"));
        return toDTO(user);
    }

    public PageResult<UserDTO> listUsers(int page, int pageSize, Role role) {
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> userPage;
        if (role != null) {
            userPage = userRepository.findByRole(role, pageRequest);
        } else {
            userPage = userRepository.findAll(pageRequest);
        }

        List<UserDTO> dtos = userPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return PageResult.of(userPage.getTotalElements(), page, pageSize, dtos);
    }

    /**
     * 「可接单的人」列表。
     *
     * <p>此前完全没按角色过滤，返回所有 active 用户 —— 加了 PUBLISHER 之后，
     * 发布者也会被当成接单者返回。这里改成排除 PUBLISHER。
     *
     * <p>用"排除"而不是"只留 HANDLER"，是因为 USER 是"发布与接单都能做"的存量
     * 身份，把它排除掉会让 6 个老账号从这份名单里消失。判据是"这人能不能接单"，
     * 不是"这人是不是恰好叫 HANDLER"。
     *
     * <p>同类问题还有一处没动：UserSkillRepository.findAvailableHandlers() 的
     * JPQL 只 join 了 status、没 join role，它返回的"可用接单手"里同样会混进
     * 发布者。那个接口目前没有前端调用方，留待批次 3 一起处理。
     */
    public List<UserDTO> listHandlers() {
        return userRepository.findByStatus("active")
                .stream()
                .filter(u -> u.getRole() != Role.PUBLISHER)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDTO updateUser(Long id, UserDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "User not found"));

        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getRole() != null) {
            user.setRole(dto.getRole());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (dto.getGithubUsername() != null) {
            user.setGithubUsername(dto.getGithubUsername());
        }
        if (dto.getGithubRepos() != null) {
            user.setGithubRepos(dto.getGithubRepos());
        }

        user = userRepository.save(user);
        log.info("User updated: {}", user.getUsername());
        return toDTO(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "User not found"));

        user.softDelete();
        userRepository.save(user);
        log.info("User deleted: {}", user.getUsername());
    }

    // ==================== 头像 ====================

    /**
     * 上传自定义头像。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code AiQuotaGate} 是同一个理由：
     * 传 OSS 是一次网络调用，不该夹在事务里占着数据库连接（Hikari 池只有 10 条）。
     * 真正的写库只有一次单行 save，{@code SimpleJpaRepository} 自己就带了短事务。
     *
     * <p>顺序是**先传 OSS、再写库**：反过来的话，写库成功而上传失败就会留下一个指向
     * 不存在对象的 avatar_url，前端表现为裂图；现在这样最坏只是留一个孤儿对象，
     * 而孤儿对象下次换头像时会被同一个 key 前缀下的新对象自然取代。
     */
    public UserDTO updateAvatar(Long userId, MultipartFile file) {
        User user = requireUser(userId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的图片");
        }
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw new BusinessException(400, "头像不能超过 2MB");
        }
        String contentType = file.getContentType() == null
                ? null : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType == null || !AVATAR_ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(400, "头像只支持 JPG / PNG / WebP / GIF 格式");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(500, "读取上传文件失败");
        }

        // key 带 userId 是为了排查时一眼看出这图是谁的；带随机 uuid 是为了让**每次换头像
        // 都是新 key** —— 若固定成 avatar/{userId}.png，浏览器会命中旧图的缓存，
        // "换了头像但页面没变"会成为一个反复出现的假 bug。
        String key = "avatar/" + userId + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + extensionOf(contentType);
        ossStorage.upload(key, content, contentType);

        String oldAvatar = user.getAvatarUrl();
        user.setAvatarUrl(key);
        user = userRepository.save(user);
        deleteOldObject(oldAvatar);

        log.info("Avatar updated: userId={}, key={}, size={}B", userId, key, content.length);
        return toDTO(user);
    }

    /**
     * 换成一个默认头像。
     *
     * <p>只接受 {@code default:1} ~ {@code default:9}。**不接受任意 URL** —— 那等于让
     * 客户端往 avatar_url 里写任何地址，其他用户的 img 会去请求它，是个现成的投毒口子。
     *
     * <p>前端的「换一个」按钮是自己摇一个数再传上来，服务端不为"帮我随机"多开口子。
     */
    public UserDTO setDefaultAvatar(Long userId, String avatarUrl) {
        User user = requireUser(userId);

        if (!isDefaultAvatar(avatarUrl)) {
            throw new BusinessException(400,
                    "无效的默认头像标识，应为 " + DEFAULT_AVATAR_PREFIX + "1 ~ "
                            + DEFAULT_AVATAR_PREFIX + DEFAULT_AVATAR_COUNT);
        }

        String oldAvatar = user.getAvatarUrl();
        user.setAvatarUrl(avatarUrl);
        user = userRepository.save(user);
        deleteOldObject(oldAvatar);

        log.info("Avatar set to default: userId={}, avatarUrl={}", userId, avatarUrl);
        return toDTO(user);
    }

    /**
     * 匿名可读：按用户名查一个头像，给登录卡"边输边显示头像"用。
     *
     * <p>**查不到时返回 null 而不是 404**。这不是偷懒：前端的响应拦截器把所有非 200 的
     * 业务码都弹成 toast，登录卡每敲一次用户名就弹一句"User not found"，等于把正常的
     * 输入过程变成报错现场。未知用户在这里是正常状态，不是错误。
     *
     * <p>返回体刻意只有 username / nickname / avatarUrl —— 这条路径在网关白名单上，
     * 匿名可达，email、role 之类的字段一个都不能带出去（见 {@link UserAvatarDTO}）。
     */
    public UserAvatarDTO getAvatarByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(u -> UserAvatarDTO.builder()
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .avatarUrl(displayAvatar(u.getAvatarUrl()))
                        .build())
                .orElse(null);
    }

    /**
     * 批量取头像，给大厅一页卡片上的发布者用（一次请求解掉 N+1）。
     *
     * <p>返回 id → 头像地址。**不存在的 id 不出现在结果里**，不补 null 占位 ——
     * 前端拿到缺失的 id 自然走首字母兜底，占位反而会让"没查到的空白"和
     * "查到了但没头像"混成同一种状态。
     */
    public Map<Long, String> getAvatars(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (User user : userRepository.findAllById(distinct)) {
            String avatar = displayAvatar(user.getAvatarUrl());
            if (avatar != null) {
                result.put(user.getId(), avatar);
            }
        }
        return result;
    }

    /**
     * 库里存的 {@code avatar_url} → 前端能直接用的地址。
     *
     * <ul>
     *   <li>{@code default:N} 原样返回，前端映射到 {@code /avatars/N.svg}；</li>
     *   <li>OSS 对象 key 拼成 https 公网地址（bucket 是公共读，见 OssStorage.publicUrl）；</li>
     *   <li>OSS 没配好时返回 null —— 宁可让前端显示首字母兜底，也不要把裸 key 交给
     *       {@code <img>}：{@code src="avatar/1/x.png"} 会去请求当前站点下的相对路径，
     *       裂得毫无线索。</li>
     * </ul>
     */
    private String displayAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()
                || avatarUrl.startsWith(DEFAULT_AVATAR_PREFIX)) {
            return avatarUrl;
        }
        return ossStorage.publicUrl(avatarUrl);
    }

    /** 取当前登录用户；userId 为 null 说明网关没注入身份（未登录或直连） */
    private User requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "User not found"));
    }

    /** 合法取值是 1..{@link #DEFAULT_AVATAR_COUNT}，越界一律拒掉 */
    private static boolean isDefaultAvatar(String value) {
        if (value == null || !value.startsWith(DEFAULT_AVATAR_PREFIX)) {
            return false;
        }
        try {
            int n = Integer.parseInt(value.substring(DEFAULT_AVATAR_PREFIX.length()));
            return n >= 1 && n <= DEFAULT_AVATAR_COUNT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 旧值是不是"我们自己 OSS 里的对象 key"。
     *
     * <p>判据取"不等于默认头像前缀"而不是"看起来像个路径"：库里只可能存这两种形态
     * （写入路径只有 {@link #updateAvatar} 和 {@link #setDefaultAvatar}，都由本类把守）。
     * 存量 NULL（migration 没跑过的库）判成 false —— 没有旧对象可删。
     */
    private static boolean isOssKey(String value) {
        return value != null && !value.startsWith(DEFAULT_AVATAR_PREFIX);
    }

    /**
     * 删掉被替换下来的旧头像对象。
     *
     * <p>只删 OSS key。把 {@code "default:3"} 当 key 去删会报 KeyNotFound ——
     * 虽然 {@link OssStorage#delete} 会吞掉异常，但那样每次换默认头像都打一条 warn，
     * 是没意义的噪音。
     */
    private void deleteOldObject(String oldAvatar) {
        if (isOssKey(oldAvatar)) {
            ossStorage.delete(oldAvatar);
        }
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
    }

    private static String randomDefaultAvatar() {
        return DEFAULT_AVATAR_PREFIX
                + (1 + ThreadLocalRandom.current().nextInt(DEFAULT_AVATAR_COUNT));
    }

    public UserSkillDTO getUserSkill(Long userId) {
        UserSkill skill = userSkillRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(404, "User skill profile not found"));
        return toSkillDTO(skill);
    }

    @Transactional
    public UserSkillDTO updateUserSkill(UpdateSkillRequest request) {
        UserSkill skill = userSkillRepository.findByUserId(request.getUserId())
                .orElseGet(() -> {
                    User user = userRepository.findById(request.getUserId())
                            .orElseThrow(() -> new BusinessException(404, "User not found"));
                    UserSkill newSkill = new UserSkill();
                    newSkill.setUserId(user.getId());
                    newSkill.setUserName(user.getNickname());
                    newSkill.setCurrentLoad(0);
                    newSkill.setMaxLoad(10);
                    return newSkill;
                });

        if (request.getSkillTags() != null) {
            skill.setSkillTags(request.getSkillTags());
        }
        if (request.getExpertiseLevel() != null) {
            skill.setExpertiseLevel(request.getExpertiseLevel());
        }
        if (request.getMaxLoad() != null) {
            skill.setMaxLoad(request.getMaxLoad());
        }

        skill = userSkillRepository.save(skill);
        log.info("User skill updated for userId: {}", request.getUserId());
        return toSkillDTO(skill);
    }

    public List<UserSkillDTO> listAvailableHandlers() {
        return userSkillRepository.findAvailableHandlers()
                .stream()
                .map(this::toSkillDTO)
                .collect(Collectors.toList());
    }

    public List<UserSkillDTO> getHandlerSkills(List<Long> handlerIds) {
        return userSkillRepository.findByUserIdIn(handlerIds)
                .stream()
                .map(this::toSkillDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void incrementHandlerLoad(Long handlerId) {
        int updated = userSkillRepository.incrementLoad(handlerId);
        if (updated == 0) {
            throw new BusinessException("Handler is at max capacity");
        }
    }

    @Transactional
    public void decrementHandlerLoad(Long handlerId) {
        userSkillRepository.decrementLoad(handlerId);
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(displayAvatar(user.getAvatarUrl()))
                .role(user.getRole())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .githubUsername(user.getGithubUsername())
                .githubRepos(user.getGithubRepos())
                .build();
    }

    private UserSkillDTO toSkillDTO(UserSkill skill) {
        return UserSkillDTO.builder()
                .id(skill.getId())
                .userId(skill.getUserId())
                .userName(skill.getUserName())
                .skillTags(skill.getSkillTags())
                .expertiseLevel(skill.getExpertiseLevel())
                .totalResolved(skill.getTotalResolved())
                .avgResolutionHours(skill.getAvgResolutionHours())
                .satisfactionScore(skill.getSatisfactionScore())
                .currentLoad(skill.getCurrentLoad())
                .maxLoad(skill.getMaxLoad())
                .build();
    }
}
