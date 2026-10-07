package com.aiticket.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TicketStatsDTO {
    private long open;
    private long accepted;
    private long pendingApproval;
    private long completed;

    /**
     * 待我验收（我是发布者、这单在 PENDING_APPROVAL 等我点验收）。
     *
     * 为什么不直接改 pendingApproval 的语义：那个字段是按 handlerId 数的，
     * 管理端 Stats.vue 还在用。多一个字段而不是改旧字段的含义，管理端就不会
     * 被静默改掉行为。
     *
     * 大厅「待验收」卡必须用这个 —— PENDING_APPROVAL 下该动作的人是 creator，
     * 按 handlerId 数恒为 0（就是那个"卡片点开空表"的 bug）。
     */
    private long pendingApprovalAsCreator;

    /** 我参与的已完成（我是发布者，或我是接单人）。大厅「已完成」卡用这个。 */
    private long completedAsParticipant;

    // ---------------- 以下 4 个是给大厅分身份卡片用的 ----------------
    //
    // 全部**只加不删不改**：open/accepted/pendingApproval/completed 四个老字段是管理端
    // Stats.vue 在用，pendingApprovalAsCreator/completedAsParticipant 是批次 1 加的。
    // 改旧字段的含义会让管理端被静默改掉行为，所以宁可字段变多。

    /** 我发布的，**不限状态**。大厅「我发布的」卡。 */
    private long createdTotal;

    /** 我接的单，**不限状态**。大厅「我接的单」卡。 */
    private long handledTotal;

    /**
     * 我发布的、还没结的单：状态 ∈ {ACCEPTED, REJECTED, PENDING_APPROVAL}。
     *
     * <p>三个状态合成一个数字，是因为「进行中」这张卡对发布者只表达一件事：
     * 我的单被别人接走了、还没验收通过。点开后按行内的状态标签区分是"开发中"
     * 还是"待我验收"。状态的唯一定义见 TicketService.ACTIVE_STATUSES。
     */
    private long activeAsCreator;

    /** 我接的、还没结的单（同上状态集）。大厅「进行中」卡（接单者视角）。 */
    private long activeAsHandler;
}
