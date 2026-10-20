package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会员订阅（**本次只建表，不接任何接口**，方案见仓库根目录 会员额度plan.txt）。
 *
 * <p>与 {@link AiCreditBatch} 的分工：**会员身份记在这张表，次数记在批次表**。
 * 开通/续费时两件事一起做 —— 插一行会员订阅、插一行 {@code source=MEMBERSHIP}
 * 且 {@code expires_at = 发放日 + 30 天} 的额度批次。
 *
 * <p>"会员到期后没用完的次数跟着作废"因此不需要额外逻辑：次数挂在批次上，
 * 批次到期就自然不再被统计。这张表存在的意义是回答"这个账号现在是不是会员、
 * 什么时候到期、下期还续不续"，这些问题靠批次表答不了（一个非会员也可能因为
 * 注册赠送而持有永久批次）。
 */
@Data
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "user_membership",
        indexes = @Index(name = "idx_membership_user", columnList = "user_id,status")
)
public class UserMembership extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 套餐标识：LITE(10次) / PRO(30次) / MAX(120次)，存量的 MONTHLY_30 是 PRO 的前身 */
    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    /** ACTIVE / EXPIRED / CANCELED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 本周期发放的次数，与批次表的 total_amount 对应，便于对账 */
    @Column(name = "credits_granted", nullable = false)
    private Integer creditsGranted;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** started_at + 30 天；到期后未用完的批次作废 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "auto_renew")
    private Boolean autoRenew = false;
}
