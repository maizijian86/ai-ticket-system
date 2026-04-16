package com.aiticket.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SuggestReplyRequest {
    @NotBlank(message = "Question is required")
    private String question;

    private Long ticketId;

    private String context;  // Previous conversation context
}
