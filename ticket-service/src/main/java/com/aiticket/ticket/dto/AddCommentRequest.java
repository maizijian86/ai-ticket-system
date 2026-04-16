package com.aiticket.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddCommentRequest {

    @NotBlank(message = "Content is required")
    private String content;

    private Boolean isInternal = false;

    private Boolean isAiSuggested = false;
}
