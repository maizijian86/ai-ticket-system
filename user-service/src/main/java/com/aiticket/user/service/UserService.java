package com.aiticket.user.service;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.common.enums.Role;
import com.aiticket.common.exception.BusinessException;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
                user.getNickname()
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
                user.getNickname()
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
