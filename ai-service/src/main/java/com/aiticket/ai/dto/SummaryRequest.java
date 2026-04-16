package com.aiticket.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SummaryRequest {
    @NotBlank(message = "Content is required")
    private String content;

    private String existingSummary;  // Existing summary to update
}
