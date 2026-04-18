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
import java.util.List;
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

    // GitHub Repositories (MySQL JSON: [{name, url}])
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "github_repos", columnDefinition = "json")
    private List<Map<String, String>> githubRepos;

    // Price
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "ai_price_suggestion", precision = 10, scale = 2)
    private BigDecimal aiPriceSuggestion;

    // Completion proof (GitHub link or other)
    @Column(name = "completion_proof", columnDefinition = "TEXT")
    private String completionProof;

    // Timestamps
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // Rejection reason
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
}
