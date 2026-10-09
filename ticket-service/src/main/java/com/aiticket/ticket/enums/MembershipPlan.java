package com.aiticket.ticket.enums;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 会员套餐 —— **价格的唯一来源**。
 *
 * <p>价格与权益文案刻意不写在前端：改价不该要重新发版，套餐文案也不该散落在两个仓库
 * 层面上。前端一律从 {@code GET /api/v1/membership/plans} 取（见 MembershipController），
 * 所以加一档「年卡」只要在这里加一个枚举值，前端一行都不用动。
 *
 * <p>⚠️ 改这里的数字要同时改前端
 * {@code frontend/src/utils/ai-quota.ts} 的 {@code MEMBERSHIP_CREDITS} / {@code MEMBERSHIP_DAYS} ——
 * 那两个常量是登录卡片与工单详情页的**文案**（"开通会员可获得 30 次"），
 * 两边不一致会出现"说 30 次、实际发 20 次"这种用户一定会发现的错误。
 *
 * <p>另外，订单表里存的是**下单那一刻**的 {@code plan_credits}/{@code plan_days} 快照，
 * 发放时用快照而不是读这个枚举 —— 否则改了套餐会把历史未支付订单按新配置发出去。
 */
public enum MembershipPlan {

    /** 会员月卡：30 元 / 30 天 / 30 次。一期只有这一档，别自作主张加档位 */
    MONTHLY_30("会员月卡", new BigDecimal("30.00"), 30, 30,
            "30 天内 30 次 AI 辅助开发额度");

    private final String name;
    private final BigDecimal price;
    private final int credits;
    private final int days;
    private final String description;

    MembershipPlan(String name, BigDecimal price, int credits, int days, String description) {
        this.name = name;
        this.price = price;
        this.credits = credits;
        this.days = days;
        this.description = description;
    }

    /** 套餐标识，也是 plan_code 的取值 */
    public String code() {
        return name();
    }

    public String displayName() {
        return name;
    }

    public BigDecimal price() {
        return price;
    }

    public int credits() {
        return credits;
    }

    public int days() {
        return days;
    }

    public String description() {
        return description;
    }

    /** 按 plan_code 查套餐。未知 code 返回空，由调用方转成 400 */
    public static Optional<MembershipPlan> of(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        for (MembershipPlan plan : values()) {
            if (plan.name().equals(code)) {
                return Optional.of(plan);
            }
        }
        return Optional.empty();
    }
}
