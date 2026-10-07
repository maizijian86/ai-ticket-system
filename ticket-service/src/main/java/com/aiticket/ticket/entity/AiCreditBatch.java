package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import com.aiticket.ticket.enums.CreditSource;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 额度批次 —— 一行 = 一次发放，同时也是"这个账号还剩多少次"的账本。
 *
 * <p>账户余额不是单独一张表里的一个数字，而是各批次剩余量之和（只统计未过期的）：
 * <pre>
 *   SUM(remaining_amount)
 *   WHERE user_id = ? AND remaining_amount &gt; 0
 *     AND (expires_at IS NULL OR expires_at &gt; NOW())
 * </pre>
 * 这样做换来两件事：一是**过期作废是天然的** —— 会员批次到期后自然不再被选中，
 * 不需要任何定时任务去核销；二是每一笔发放都可追溯（谁在什么时候送了几次、
 * 什么原因、什么时候过期）。
 *
 * <p>批次而不是单余额，是因为"会员到期后没用完的次数跟着作废"这条规则要求
 * 不同来源的额度有不同的有效期。一个 account.balance 字段表达不了它 ——
 * 除非再补一张表记录每个批次的剩余量，那就等于绕回这张表。
 */
@Data
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "ai_credit_batch",
        uniqueConstraints = @UniqueConstraint(name = "uk_batch_biz_key", columnNames = "biz_key"),
        // 余额查询恒为 user_id + 过期时间的组合，这个索引是它唯一的支撑。
        // 必须显式声明：@UniqueConstraint 只会生成唯一键，不会顺带生成普通索引。
        indexes = @Index(name = "idx_batch_user_expiry", columnList = "user_id, expires_at")
)
public class AiCreditBatch extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 发放来源：注册赠送 / 会员 / 管理员调整 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CreditSource source;

    /**
     * 发放幂等键：{@code SIGNUP:{userId}} / {@code MEMBERSHIP:{orderId}} / {@code ADMIN:{uuid}}。
     *
     * <p>唯一键 + {@code INSERT IGNORE} 让"发放"这件事天然幂等 —— 重复调用（比如
     * 一个新用户同时打开了两个请求，两处都触发首次赠送）只会留下第一批。
     * 没有它就得先查再插，而"先查再插"在并发下挡不住。
     */
    @Column(name = "biz_key", nullable = false, length = 100)
    private String bizKey;

    @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;

    /** 本批次剩余可用次数，扣减直接改它 */
    @Column(name = "remaining_amount", nullable = false)
    private Integer remainingAmount;

    /** 过期时间。NULL = 永久有效（注册赠送走这条） */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "remark", length = 255)
    private String remark;
}
