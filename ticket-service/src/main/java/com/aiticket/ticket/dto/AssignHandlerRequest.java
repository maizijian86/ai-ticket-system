package com.aiticket.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignHandlerRequest {

    @NotNull(message = "Handler ID is required")
    private Long handlerId;
}
