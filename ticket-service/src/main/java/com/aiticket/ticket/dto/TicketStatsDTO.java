package com.aiticket.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TicketStatsDTO {
    private long open;
    private long accepted;
    private long pendingApproval;
    private long completed;
}
