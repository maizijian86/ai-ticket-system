package com.aiticket.ticket.dto;

import com.aiticket.common.enums.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateTicketRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must be less than 500 characters")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    private Urgency urgency = Urgency.NORMAL;

    private List<Map<String, String>> attachments; // [{name, path, size, type}]
}
