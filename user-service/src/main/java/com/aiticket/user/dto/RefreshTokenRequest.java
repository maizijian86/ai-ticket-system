package com.aiticket.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
