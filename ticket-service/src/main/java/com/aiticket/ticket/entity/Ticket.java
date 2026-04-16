package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import com.aiticket.common.enums.Priority;
import com.aiticket.common.enums.TicketCategory;
import com.aiticket.common.enums.TicketStatus;
import com.aiticket.common.enums.Urgency;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ticket")
public class Ticket extends BaseEntity {

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // AI Classification
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private TicketCategory category;

    @Column(name = "category_confidence", precision = 5, scale = 2)
    private BigDecimal categoryConfidence;

    // AI Priority
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority = Priority.P2;

    @Column(name = "priority_score", precision = 5, scale = 2)
    private BigDecimal priorityScore;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    // User-selected urgency
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Urgency urgency = Urgency.NORMAL;

    // Submitter
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "creator_name")
    private String creatorName;

    // Handler
    @Column(name = "handler_id")
    private Long handlerId;

    @Column(name = "handler_name")
    private String handlerName;

    // AI Recommendation
    @Column(name = "recommended_handler_id")
    private Long recommendedHandlerId;

    @Column(name = "recommend_reason", columnDefinition = "TEXT")
    private String recommendReason;

    // AI Summary
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    // Attachments (MySQL JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> attachments;

    // Timestamps
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
