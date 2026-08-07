package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目任务实体 - 具体开发任务
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "project_task")
public class ProjectTask extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "module_id")
    private Long moduleId;

    @Column(name = "parent_task_id")
    private Long parentTaskId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Task type: FEATURE, BUG, TASK, REFACTOR
    @Column(name = "task_type", length = 30)
    private String taskType;

    // Status: TODO, IN_PROGRESS, REVIEW, DONE
    @Column(length = 30)
    private String status = "TODO";

    @Column(length = 10)
    private String priority = "P2";

    // Work hours
    @Column(name = "estimated_hours", precision = 8, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "actual_hours", precision = 8, scale = 2)
    private BigDecimal actualHours;

    // AI evaluation
    @Column(name = "ai_estimated_hours", precision = 8, scale = 2)
    private BigDecimal aiEstimatedHours;

    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    private String aiSuggestion;

    // Time
    private LocalDate deadline;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Assignee
    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "assignee_name", length = 100)
    private String assigneeName;
}
