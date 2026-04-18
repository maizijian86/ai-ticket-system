package com.aiticket.user.dto;

import com.aiticket.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String password;

    @Email(message = "Invalid email format")
    private String email;

    @Size(max = 100, message = "Nickname must be less than 100 characters")
    private String nickname;

    private Role role; // Optional, defaults to USER if not specified

    private String githubUsername;

    private List<Map<String, String>> githubRepos;
}
