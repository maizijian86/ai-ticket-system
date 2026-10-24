package com.aiticket.ticket.enums;

/**
 * 充值订单的状态。与 {@code MembershipOrderStatus} 同构，但**语义上更严格**：
 * 这里的 PAID 意味着「钱包可用余额已经加上了」，所以守卫必须比会员那条更小心。
 *
 * <pre>
 *   PENDING → PAID     到账成功（异步回调，或结果页的主动查单兜底）
 *   PENDING → CLOSED   支付宝侧确认交易已关闭 / 查不到这笔交易
 * </pre>
 *
 * <p><b>CLOSED 不等于「这笔钱一定没付」</b>：与我们关单的那一刻相比，支付宝那边的交易
 * 往往还活着（我们判据阈值比支付宝的交易超时短得多）。用户完全可能从支付宝的交易列表里
 * 把旧单付掉。所以到账逻辑的守卫**只挡 PAID、不挡 CLOSED** —— 钱真到账就必须记账，
 * 否则就是「收了钱不给余额」。详见 {@code WalletService.fulfillRecharge}。
 */
public enum WalletOrderStatus {
    /** 已下单，还没收到钱 */
    PENDING,
    /** 已到账，余额已增加 */
    PAID,
    /** 已关闭：支付宝侧确认未成交 */
    CLOSED
}
