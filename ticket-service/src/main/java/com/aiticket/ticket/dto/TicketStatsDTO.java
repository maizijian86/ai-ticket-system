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

    // ---------------- 个人统计页用（2026-10 加） ----------------
    //
    // 三个字段都是**查询结果**，不是新存储的列 —— 没有 ALTER TABLE。
    // 金额读的是钱包域的真实结算记录，不是 SUM(budget)：
    //   · budget 在"未接单时"发布者可以改，与"当初结算了多少"不是一回事；
    //   · 钱包上线前创建的历史工单没有托管行、从未结算，用 budget 会把它们算成收入。

    /** 我**发布**的、已完成的数量。个人统计的「发布完成率」分子 */
    private long completedAsCreator;

    /**
     * 我**实际收到的**工单款合计（真钱）。
     *
     * <p>= `SUM(wallet_transaction.amount) WHERE user_id=我 AND type='SETTLE_IN'`。
     * `SETTLE_IN` 是"钱进我可用余额"的唯一动作，一笔一条，天然排除历史工单。
     *
     * <p>注意：这是**钱包余额里**的钱，不是银行卡；不可提现。
     */
    private java.math.BigDecimal settledIncome;

    /**
     * 我**实际付出去的**工单款合计（真钱）。
     *
     * <p>= `SUM(ticket_escrow.amount) WHERE publisher_id=我 AND status='SETTLED'`。
     * 优于用流水的 `SETTLE_OUT` 反推：一张单只有一行 SETTLED，金额就是结算金额，
     * 而且不用掰符号（`SETTLE_OUT` 的 amount 是 0，金额在 frozen_delta 上）。
     */
    private java.math.BigDecimal settledPayout;
}
