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
    private Role role;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    private String githubUsername;
    private List<Map<String, String>> githubRepos;
}
