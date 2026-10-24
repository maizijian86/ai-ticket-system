package com.aiticket.ticket.enums;

/**
 * 工单资金托管的状态。
 *
 * <pre>
 *   FROZEN ──→ SETTLED    验收通过，钱转给接单者（终态，不可逆）
 *   FROZEN ──→ REFUNDED   工单关闭 / 删除，钱退回发布者可用余额
 * </pre>
 *
 * <p><b>为什么一个工单会有多行</b>：{@code round} 表示第几次托管。
 * 工单被关闭后「重新开放」会开启新一轮托管（{@code round+1}），
 * 旧那行保持 {@code REFUNDED} 不动 —— 历史不覆盖，才查得清。
 *
 * <p><b>SETTLED 之后这张工单不能再重开</b>：钱已经给了接单者，
 * 重开只有三条路（从接单者扣回 / 白送 / 让第二轮不结算），全都不成立。
 * 见 {@code TicketService.reopen}。
 */
public enum EscrowStatus {
    /** 托管中：钱在发布者的冻结额度里，等着验收或退回 */
    FROZEN,
    /** 已结算给接单者 */
    SETTLED,
    /** 已退回发布者可用余额 */
    REFUNDED
}
