package com.aiticket.user.controller;

import com.aiticket.common.dto.Result;
import com.aiticket.user.dto.LoginRequest;
import com.aiticket.user.dto.LoginResponse;
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
}
