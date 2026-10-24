package com.aiticket.ticket.entity;

import com.aiticket.ticket.enums.WalletTxType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水 —— 纯追加的**审计账本**。
 *
 * <p>真相在 {@link UserWallet}（当前余额）和 {@link TicketEscrow}（当前托管额），
 * 这张表负责回答「为什么变成了这样」。它只增不改，没有 deleted_at / updated_at。
 *
 * <p><b>两个变动量分成两列</b>：
 * <ul>
 *   <li>{@link #amount} —— 可用余额的变动</li>
 *   <li>{@link #frozenDelta} —— 冻结额的变动</li>
 * </ul>
 * 因为「验收结算」时发布者的可用余额并不变（变的是冻结），只记一个金额的话这条流水
 * 就是 0，审计看不出结算了多少钱。于是两条对账恒等式各自成立：
 * <pre>
 *   available_amount = SUM(amount)
 *   frozen_amount    = SUM(frozen_delta)
 * </pre>
 *
 * <p><b>{@code uniq_key} 是幂等兜底</b>：{@code TICKET:57:1:REFUND} 这样的键全局唯一，
 * 挡住"同一笔验收被处理两次、同一笔回调入账两次"这类事故。
 * ⚠️ 但 {@code ESCROW_ADJUST}（改预算）在同一轮里会有多条，所以它的
 * {@link #uniqKey} **留 null** —— MySQL 的唯一索引不约束含 NULL 的行。
 * 注意别反过来用 {@link #escrowRound} 留空来躲键：那样结算前的交叉校验会漏算调额。
 */
@Data
@Entity
@Table(
        name = "wallet_transaction",
        // 幂等键只有一个列：只有"可能重复执行"的动作才填，
        // 改预算（同一轮可以很多条）留 NULL —— MySQL 唯一索引不约束含 NULL 的行。
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_tx_uniq", columnNames = "uniq_key"),
        indexes = @Index(name = "idx_wallet_tx_user", columnList = "user_id, created_at")
)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private WalletTxType type;

    /** 可用余额变动：正=入账，负=出账 */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 冻结额变动：正=转入托管，负=转出 */
    @Column(name = "frozen_delta", nullable = false, precision = 12, scale = 2)
    private BigDecimal frozenDelta = BigDecimal.ZERO;

    /** 本次变动后的可用余额，用于对账时定位"从哪一步开始不对" */
    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    /** TICKET / WALLET_ORDER */
    @Column(name = "ref_type", length = 16)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    /**
     * 第几次托管。**所有 TICKET 流水都填真实轮次**（包括改预算），
     * 因为结算前要按轮次交叉校验"流水累计冻结额 == escrow.amount"。
     */
    @Column(name = "escrow_round")
    private Integer escrowRound;

    @Column(name = "remark", length = 255)
    private String remark;

    /**
     * 幂等键，形如 {@code TICKET:57:1:REFUND} / {@code WALLET_ORDER:1:RECHARGE}。
     *
     * <p>只有**可能重复执行**的动作才填（支付宝回调、验收结算、关闭退回）；
     * 改预算（{@code ESCROW_ADJUST}）同一轮可以有很多条，留 null 让唯一索引放行。
     *
     * <p>为什么不把轮次写 null 来达到同样效果：那样结算前的交叉校验就会漏算调额，
     * 误判"资金记录不一致"而拒绝结算 —— 这是实测踩过的坑。
     */
    @Column(name = "uniq_key", length = 80)
    private String uniqKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
