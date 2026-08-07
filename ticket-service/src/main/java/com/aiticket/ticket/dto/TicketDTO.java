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

    // GitHub Repositories
    private List<Map<String, String>> githubRepos;

    // Price
    private BigDecimal price;
    private BigDecimal aiPriceSuggestion;

    // Completion proof
    private String completionProof;

    // Rejection reason
    private String rejectionReason;

    // ==================== 开发者需求字段 ====================

    // 技术栈
    private String techStack;

    // 项目类型
    private String projectType;

    // 预算
    private BigDecimal budget;

    // 截止日期
    private LocalDateTime deadline;

    // 详细需求描述
    private String detailedRequirements;

    // ==================== AI生成内容 ====================

    // AI生成的PRD文档
    private String aiPrd;

    // AI技术栈建议
    private String aiTechSuggestion;

    // AI任务拆解
    private String aiTaskBreakdown;

    // AI工时估算
    private Integer aiEstimatedHours;

    // ==================== 接单状态 ====================

    // 接单者ID
    private Long acceptedBy;

    // 接单者姓名
    private String acceptedByName;

    // 是否在大厅显示
    private Boolean isVisible;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    // Additional info
    private List<CommentDTO> comments;
    private Long commentCount;
}
