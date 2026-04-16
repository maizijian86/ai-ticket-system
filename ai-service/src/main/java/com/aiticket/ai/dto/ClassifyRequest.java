package com.aiticket.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClassifyRequest {
    @NotBlank(message = "Content is required")
    private String content;
}
