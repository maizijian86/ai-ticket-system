package com.aiticket.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendChatRequest {
    @NotBlank(message = "Content is required")
    private String content;
}
