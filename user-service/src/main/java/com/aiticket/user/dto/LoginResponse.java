package com.aiticket.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String username;
    private String role;
    private String nickname;
    /**
     * 头像，与 UserDTO.avatarUrl 同构。
     *
     * <p>**必须放在这里**：前端的 authStore 登录后不回头调 /users/{id}，而是直接拿这个响应
     * 拼出 userInfo 存进 localStorage。登录响应里没有头像，用户登录后顶栏就是空的，
     * 要等刷新或进个人主页才会补上。
     */
    private String avatarUrl;
}
