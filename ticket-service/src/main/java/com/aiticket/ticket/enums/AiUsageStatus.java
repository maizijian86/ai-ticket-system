package com.aiticket.ticket.enums;

/**
 * 一次 AI 功能使用的结算状态。
 *
 * <p>生命周期：{@code RESERVED} →（成功）{@code SUCCESS} / （失败）{@code REFUNDED}。
 *
 * <p>为什么需要 RESERVED 这个中间态：额度必须在**调 AI 之前**先扣，否则并发点击
 * 会超发；而 AI 调用要跑几分钟，所以扣减（短事务提交）和结算（另一个短事务）之间
 * 隔着那段漫长的等待。RESERVED 就是"已扣费但结果未定"的标记 —— 进程若在等待中
 * 崩掉，它会永久留在 RESERVED，靠 {@code AiQuotaService.reclaimIfStale} 超时接管退款。
 */
public enum AiUsageStatus {
    /** 已预占额度，AI 调用进行中 */
    RESERVED,
    /** AI 调用成功，额度已实际消耗 */
    SUCCESS,
    /** AI 调用失败，额度已退回原批次、占用槽位已释放（可重试） */
    REFUNDED
}
