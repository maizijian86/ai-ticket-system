package com.aiticket.user.dto;

import com.aiticket.common.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String nickname;
    /** "default:N" 或 OSS 对象 key。前端据此分叉：前者映射本地静态图，后者直接当 URL */
    private String avatarUrl;
    private Role role;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    private String githubUsername;
    private List<Map<String, String>> githubRepos;
}
