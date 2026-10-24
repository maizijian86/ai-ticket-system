package com.aiticket.ticket.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户钱包 —— 一人一行，主键就是 {@code user_id}（不是自增 id）。
 *
 * <p>两个余额池：
 * <ul>
 *   <li>{@link #availableAmount} 可用余额：能用来发布工单</li>
 *   <li>{@link #frozenAmount} 冻结（托管）：已随某张在途工单押住，等着结算或退回</li>
 * </ul>
 *
 * <p><b>为什么不在 {@code users} 表上加一列</b>：{@code users} 归 user-service 所有，
 * 而资金动作必须与工单状态变更在**同一个本地事务**里（验收通过 = 改状态 + 转账，
 * 两者要么都成要么都不成）。跨服务就没有本地事务了。所以钱包放在 ticket-service，
 * 用 {@code user_id} 关联，不映射 {@code users} 表。
 *
 * <p><b>懒创建</b>：用户第一次看余额 / 第一次收款时才插入这一行
 *（见 {@code WalletService.walletOrCreate}）。不在登录时批量插，避免为从不涉及钱的
 * 账号留空行。
 *
 * <p>金额恒为整数元（数据库侧有 CHECK 兜底），见 {@code WalletService.MONEY_STRICT}。
 */
@Data
@Entity
@Table(name = "user_wallet")
public class UserWallet {

    /** 与 {@code users.id} 对应，但不做外键 —— 跨服务边界只存 id */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "available_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal availableAmount = BigDecimal.ZERO;

    @Column(name = "frozen_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal frozenAmount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static UserWallet of(Long userId) {
        UserWallet w = new UserWallet();
        w.setUserId(userId);
        return w;
    }

    /** 总资产（可用 + 冻结）。只用于展示，不要拿它做任何判断 */
    public BigDecimal totalAmount() {
        return availableAmount.add(frozenAmount);
    }
}
