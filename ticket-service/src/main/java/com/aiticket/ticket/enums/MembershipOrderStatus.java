package com.aiticket.ticket.enums;

/**
 * 会员订单的状态。
 *
 * <pre>
 *   PENDING → PAID     到账成功（异步回调，或结果页的主动查单兜底）
 *   PENDING → CLOSED   超时未支付、或支付宝那边交易已关闭
 * </pre>
 *
 * <p><b>PAID 是终态，没有 REFUNDED</b> —— 会员是虚拟权益，次数一开通就发出去了，
 * 本次不做退款（见仓库根目录 会员额度plan.txt 零节）。
 *
 * <p><b>CLOSED 不等于"这笔交易在支付宝那边一定没发生"</b>：我们把超时单标成 CLOSED 的
 * 那一刻，支付宝那边的交易往往还活着（我们的判据阈值比支付宝的交易超时短得多），
 * 用户完全可能从支付宝的交易列表里把旧单付掉。所以到账逻辑的守卫只挡 {@code PAID}、
 * 不挡 {@code CLOSED} —— 详见 {@code MembershipService.fulfill}。
 *
 * <p>存字符串而不是枚举序号（{@code @Enumerated(EnumType.STRING)}），与
 * {@code user_membership.status}、{@code ai_credit_batch.source} 一致。
 */
public enum MembershipOrderStatus {
    /** 已下单，还没收到钱 */
    PENDING,
    /** 已到账，会员权益已发放 */
    PAID,
    /** 已关闭：超时未支付，或支付宝侧交易已关闭 */
    CLOSED
}
