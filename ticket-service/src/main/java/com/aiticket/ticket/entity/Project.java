package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 项目实体 - 替代Ticket，代表一个完整开发项目
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "project")
public class Project extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "project_type", length = 50)
    private String projectType;  // THESIS/OUTSOURCE/INTERNAL/PERSONAL

    // AI analysis results
    @Column(name = "ai_prd", columnDefinition = "MEDIUMTEXT")
    private String aiPrd;

    @Column(name = "ai_tech_stack", length = 500)
    private String aiTechStack;

    @Column(name = "ai_estimated_hours")
    private Integer aiEstimatedHours;

    @Column(name = "ai_estimated_days")
    private Integer aiEstimatedDays;

    @Column(name = "ai_risk_analysis", columnDefinition = "TEXT")
    private String aiRiskAnalysis;

    // Status: PLANNING, IN_PROGRESS, TESTING, COMPLETED, ARCHIVED
    @Column(length = 30)
    private String status = "PLANNING";

    @Column(length = 10)
    private String priority = "P2";

    // Time management
    private LocalDate deadline;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    // Progress
    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer progress = 0;

    @Column(name = "total_tasks", columnDefinition = "INT DEFAULT 0")
    private Integer totalTasks = 0;

    @Column(name = "completed_tasks", columnDefinition = "INT DEFAULT 0")
    private Integer completedTasks = 0;

    // Creator
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "creator_name")
    private String creatorName;

    // Attachments (MySQL JSON)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> attachments;
}
