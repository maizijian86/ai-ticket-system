package com.aiticket.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TicketStatsDTO {
    private long open;
    private long processing;
    private long resolved;
    private long closed;
}
