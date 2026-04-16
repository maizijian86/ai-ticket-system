package com.aiticket.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecommendHandlerRequest {
    @NotBlank(message = "Content is required")
    private String content;

    private String category;

    private Long ticketId;
}
