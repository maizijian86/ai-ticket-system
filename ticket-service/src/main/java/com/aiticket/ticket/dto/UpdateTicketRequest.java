package com.aiticket.ticket.dto;

import com.aiticket.common.enums.Priority;
import com.aiticket.common.enums.TicketCategory;
import com.aiticket.common.enums.Urgency;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class UpdateTicketRequest {

    @Size(max = 500, message = "Title must be less than 500 characters")
    private String title;

    private String content;

    private TicketCategory category;

    private Priority priority;

    private Urgency urgency;

    private List<Map<String, String>> attachments;

    private List<Map<String, String>> githubRepos;

    private BigDecimal price;
}
