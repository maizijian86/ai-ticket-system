package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import com.aiticket.ticket.enums.WalletOrderStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单 —— 一行 = 一次「给钱包充值」的支付单据。
 *
 * <p>结构与 {@link MembershipOrder} 一模一样（同一套支付宝链路、同一个回调处理器家族），
 * 差别只有三点：
 * <ul>
 *   <li>金额是**用户自己填的**（会员是套餐定价），所以校验更严：整数元、1 ~ 50000</li>
 *   <li>单号前缀是 {@code WAL}（会员是 {@code MEM}），日志里一眼能分开</li>
 *   <li>到账动作是「可用余额 +amount」，不是发次数/开会员</li>
 * </ul>
 *
 * <p><b>未支付的单也必须落库</b>：{@code out_trade_no} 要先有唯一键才谈得上向支付宝下单、
 * 防连点的复用逻辑要有行可查、对账要有单据。
 */
@Data
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "wallet_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_order_out_trade_no", columnNames = "out_trade_no"),
        indexes = @Index(name = "idx_wallet_order_user", columnList = "user_id, status")
)
public class WalletOrder extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 商户订单号：{@code WAL + yyyyMMddHHmmss + 6 位随机数} */
    @Column(name = "out_trade_no", nullable = false, length = 64)
    private String outTradeNo;

    /** 充值金额（元，整数）。要拿它和支付宝回传的 total_amount 逐分比对 */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WalletOrderStatus status;

    @Column(name = "pay_channel", nullable = false, length = 20)
    private String payChannel;

    /** 支付宝交易号，回调里带回来。未支付时为 null */
    @Column(name = "trade_no", length = 64)
    private String tradeNo;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
