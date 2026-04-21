package com.aiticket.ticket.dto;

import com.aiticket.common.enums.Priority;
import com.aiticket.common.enums.TicketCategory;
import com.aiticket.common.enums.TicketStatus;
import lombok.Data;

@Data
public class TicketQueryRequest {
    private Long creatorId;
    private Long excludeCreatorId;
    private Long handlerId;
    private TicketStatus status;
    private TicketCategory category;
    private Priority priority;
    private String keyword;
    private Boolean includeAll;  // true: 不过滤handlerId，查所有
    private int page = 1;
    private int pageSize = 10;
}
