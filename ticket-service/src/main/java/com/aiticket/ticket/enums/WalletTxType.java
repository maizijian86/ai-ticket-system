package com.aiticket.ticket.enums;

/**
 * 钱包流水的类型。
 *
 * <p>每条流水同时记两个变动量（见 {@code wallet_transaction} 的列说明）：
 * <ul>
 *   <li>{@code amount} —— **可用余额**的变动（正=增加）</li>
 *   <li>{@code frozen_delta} —— **冻结额**的变动（正=转入托管）</li>
 * </ul>
 * 分成两列是因为「验收结算」时发布者的**可用余额并没有变**（变的是冻结），
 * 若只记一个金额，那条流水就变成 0，审计时看不出结算了多少钱。
 *
 * <p>于是两条对账恒等式各自成立：
 * <pre>
 *   available_amount = SUM(amount)
 *   frozen_amount    = SUM(frozen_delta)
 * </pre>
 */
public enum WalletTxType {

    /** 支付宝充值到账：可用 +，冻结不动 */
    RECHARGE,

    /** 发布工单，冻结预算：可用 -，冻结 + */
    FREEZE,

    /** 未接单时改预算：差额在两个池子之间转移。★ 唯一允许同轮多次出现的类型 */
    ESCROW_ADJUST,

    /** 工单关闭 / 删除，托管退回：可用 +，冻结 - */
    REFUND,

    /** 验收通过，钱离开发布者的冻结：可用不变，冻结 - */
    SETTLE_OUT,

    /** 验收通过，钱进入接单者的可用余额：可用 +，冻结不变 */
    SETTLE_IN
}
