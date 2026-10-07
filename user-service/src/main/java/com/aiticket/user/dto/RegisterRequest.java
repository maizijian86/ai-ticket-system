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

    /**
     * 注册身份，可选。
     *
     * <p>**不是**任意枚举值都能用：只接受 PUBLISHER / HANDLER，见
     * UserService.SELF_REGISTERABLE_ROLES。传 ADMIN 会被 400 拒掉（此前是原样
     * 落库的，等于一个对外可用的提权入口）。
     *
     * <p>留 null 仍然落到 USER，只为不破坏已有调用方；新前端一定会带值。
     */
    private Role role;

    private String githubUsername;

    private List<Map<String, String>> githubRepos;
}
