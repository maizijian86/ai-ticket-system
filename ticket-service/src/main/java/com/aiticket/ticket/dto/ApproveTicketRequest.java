package com.aiticket.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveTicketRequest {
    @NotNull(message = "approved is required")
    private Boolean approved;

    // Required when approved = false
    private String reason;
}
