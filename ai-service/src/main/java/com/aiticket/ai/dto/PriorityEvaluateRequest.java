package com.aiticket.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PriorityEvaluateRequest {
    @NotBlank(message = "Content is required")
    private String content;

    private String urgency;  // LOW, NORMAL, HIGH, CRITICAL

    private String category;  // Bug, CONSULT, COMPLAINT, SUGGESTION, OTHER

    private Long userId;  // For user level consideration
}
