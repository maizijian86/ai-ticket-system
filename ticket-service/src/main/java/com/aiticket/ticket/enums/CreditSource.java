package com.aiticket.ticket.enums;

/**
 * 一笔 AI 额度的来源。
 *
 * <p>额度按"批次"记账（一行 = 一次发放），过期时间挂在批次上，所以"会员到期后
 * 没用完的次数跟着作废"不需要任何额外的核销逻辑 —— 只要给会员批次设了
 * {@code expires_at}，过期后它自然不再被选中。
 *
 * <p>注册赠送走 {@code REGISTER} 且 {@code expires_at} 为空（永久有效）。
 * 会员发放走 {@code MEMBERSHIP}。管理员调整走 {@code ADMIN} —— 这一路目前没有
 * 界面，但幂等键 {@code ADMIN:{uuid}} 已经预留。
 */
public enum CreditSource {
    /** 新用户注册赠送（5 次，永久有效） */
    REGISTER,
    /** 会员按月发放（Lite 10 / Pro 30 / Max 120 次，一律自发放日起 30 天有效） */
    MEMBERSHIP,
    /** 管理员手动调整 */
    ADMIN
}
