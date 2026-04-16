package com.aiticket.ticket.dto;

import com.aiticket.common.enums.Priority;
import com.aiticket.common.enums.TicketCategory;
import com.aiticket.common.enums.TicketStatus;
import com.aiticket.common.enums.Urgency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDTO {

    private Long id;
    private String title;
    private String content;

    // AI fields
    private TicketCategory category;
    private BigDecimal categoryConfidence;
    private Priority priority;
    private BigDecimal priorityScore;

    // Status & Urgency
    private TicketStatus status;
    private Urgency urgency;

    // Submitter
    private Long creatorId;
    private String creatorName;

    // Handler
    private Long handlerId;
    private String handlerName;

    // AI recommendation
    private Long recommendedHandlerId;
    private String recommendReason;

    // AI summary
    private String aiSummary;

    // Attachments
    private List<Map<String, String>> attachments;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    // Additional info
    private List<CommentDTO> comments;
    private Long commentCount;
}
