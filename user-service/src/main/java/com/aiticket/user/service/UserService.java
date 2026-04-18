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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public UserDTO register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(400, "Username already exists");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(400, "Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setRole(request.getRole() != null ? request.getRole() : Role.USER);
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

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());

        log.info("User logged in: {}", user.getUsername());

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getNickname()
        );
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

    public List<UserDTO> listHandlers() {
        return userRepository.findByStatus("active")
                .stream()
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
