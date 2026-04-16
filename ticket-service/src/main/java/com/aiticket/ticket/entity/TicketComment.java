package com.aiticket.ticket.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "ticket_comment")
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Internal note (handler only)
    @Column(name = "is_internal")
    private Boolean isInternal = false;

    // AI suggested reply
    @Column(name = "is_ai_suggested")
    private Boolean isAiSuggested = false;

    // AI reference sources (MySQL JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_references", columnDefinition = "json")
    private List<Map<String, Object>> references;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", insertable = false, updatable = false)
    private Ticket ticket;
}
