package com.aiticket.user.controller;

import com.aiticket.common.dto.Result;
import com.aiticket.common.exception.BusinessException;
import com.aiticket.user.dto.LoginRequest;
import com.aiticket.user.dto.LoginResponse;
import com.aiticket.user.dto.RefreshTokenRequest;
import com.aiticket.user.dto.RegisterRequest;
import com.aiticket.user.dto.UserDTO;
import com.aiticket.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    /**
     * 刷新Token - Access Token过期时使用Refresh Token获取新的Access Token
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(userService.refreshToken(request.getUserId(), request.getRefreshToken()));
    }

    /**
     * 用户登出 - 删除Redis中的Refresh Token
     *
     * userId 取自网关注入的 X-User-Id（来源是已校验的 Access Token），不能用请求参数传：
     * 那样任何人都能凭一个 userId 把别人踢下线。该路径已不在网关白名单里，未登录会被网关拦掉。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        userService.logout(userId);
        return Result.success();
    }
}
