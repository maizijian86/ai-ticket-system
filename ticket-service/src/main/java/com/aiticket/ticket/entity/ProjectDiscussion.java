package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目讨论实体 - 项目/模块/任务的讨论记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "project_discussion")
public class ProjectDiscussion extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // Target type: PROJECT, MODULE, TASK
    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_ai_suggested", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isAiSuggested = false;
}
