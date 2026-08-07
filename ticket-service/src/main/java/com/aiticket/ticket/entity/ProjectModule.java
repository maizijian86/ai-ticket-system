package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目模块实体 - 项目下的功能模块
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "project_module")
public class ProjectModule extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", columnDefinition = "INT DEFAULT 0")
    private Integer sortOrder = 0;

    // Status: PENDING, IN_PROGRESS, COMPLETED
    @Column(length = 30)
    private String status = "PENDING";

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer progress = 0;

    @Column(name = "total_tasks", columnDefinition = "INT DEFAULT 0")
    private Integer totalTasks = 0;

    @Column(name = "completed_tasks", columnDefinition = "INT DEFAULT 0")
    private Integer completedTasks = 0;
}
