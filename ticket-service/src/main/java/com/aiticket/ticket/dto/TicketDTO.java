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

    // 发布者发布时是否预附了 1 次 AI 额度（仅用于大厅置顶/打标与详情页说明，
    // 判断「AI 是否已解锁」仍然看 AiQuotaStatusDTO，不要用这个字段替代）
    private Boolean aiPrepaid;

    // 发布者在发这张单时是带「大厅优先曝光」权益的会员（目前只有 Max 档）。
    // 与 aiPrepaid 同构：只用于大厅置顶与打标，不参与任何权限判断。
    // 前端可以把它的置顶样式做得比 aiPrepaid 更显眼（两者都置顶时它排更前）
    private Boolean memberPromoted;

    // ==================== 接单状态 ====================

    // 接单者用 handlerId / handlerName，不再另设 acceptedBy（理由见 Ticket 实体）

    // 是否在大厅显示
    private Boolean isVisible;

    /**
     * 这张单是否**有资金托管**（创建时冻结过预算）。
     *
     * <p>钱包功能上线**之前**创建的工单没有托管记录、也没冻结过钱，它们走完流程时
     * 不会发生任何结算（见 WalletService.settleTicket 的"无托管则跳过"）。
     * 前端据此在详情页/验收弹窗提示"此工单创建于资金功能上线前，不涉及资金结算"，
     * 免得用户以为"我付了预算怎么没扣钱"或者"我干完了怎么没收到钱"。
     *
     * <p>只在该工单的单条查询里填充（列表接口不填，避免每行多一次查询）：
     * {@code null} 表示"未查"，{@code false} 才是"确实没有托管"。
     */
    private Boolean funded;

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
