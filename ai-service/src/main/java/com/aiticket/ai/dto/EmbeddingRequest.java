package com.aiticket.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmbeddingRequest {
    @NotBlank(message = "Text is required")
    private String text;
}
