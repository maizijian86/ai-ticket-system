package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 里程碑实体 - 项目关键节点
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "milestone")
public class Milestone extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    // Status: PENDING, ACHIEVED, MISSED
    @Column(length = 30)
    private String status = "PENDING";

    @Column(name = "sort_order", columnDefinition = "INT DEFAULT 0")
    private Integer sortOrder = 0;
}
