package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import com.aiticket.ticket.enums.MembershipOrderStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员订单 —— 一行 = 一次「开通会员」的支付单据。
 *
 * <p>与 {@link UserMembership} / {@link AiCreditBatch} 的分工：订单回答「这一期是谁付的
 * 钱、付了多少、在支付宝那边是哪一笔」；会员身份在 {@link UserMembership}；次数在
 * {@link AiCreditBatch}。三者由 {@code MembershipService.fulfill} 在**同一个事务**里落地。
 *
 * <p><b>这是业务单据，不是支付宝的支付流水。</b>支付宝那边的 {@code trade_no} 是资金凭据，
 * 生命周期由支付宝说了算（会超时关闭、记录会老）；我们这张单永远留着。所以
 * 「会员有没有开通」一律只看这张表与 {@link UserMembership}，不看支付宝 ——
 * {@code trade_status} 只用来驱动一次 {@code PENDING → PAID} 的跃迁。
 *
 * <p><b>未支付的单也必须落库</b>（不是"等支付成功再插"）：{@code out_trade_no} 要先有
 * 唯一键才谈得上向支付宝下单、防连点的复用逻辑要有行可查、对账要有单据。而且支付宝侧
 * 未支付的交易随时可能因超时消失，把订单事实寄托在它的生命周期上等于没有事实。
 */
@Data
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "membership_order",
        // 回调只能带着 out_trade_no 回来，这张唯一键既是反查依据，也是
        // 「一个单号只对应一次支付尝试」的最后兜底（业务侧还有一道 10 分钟复用闸）。
        // 必须显式声明：@UniqueConstraint 不会顺带生成普通索引。
        uniqueConstraints = @UniqueConstraint(name = "uk_order_out_trade_no", columnNames = "out_trade_no"),
        // 「我的订单」恒为 user_id + status 的组合查询（列表接口刻意不过滤状态，
        // 见 MembershipService.listOrders），这个索引是它唯一的支撑。
        indexes = @Index(name = "idx_order_user", columnList = "user_id, status")
)
public class MembershipOrder extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 下单时的套餐标识：LITE / PRO / MAX（存量的 MONTHLY_30 是 PRO 的前身） */
    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    /**
     * 权益快照：下单那一刻该套餐发放的次数。
     *
     * <p><b>不是冗余字段。</b>到账发放要用"下单时"的次数，而不是"到账时"的套餐配置 ——
     * 否则以后把套餐从 30 次改成 20 次，所有历史未支付订单会按新配置发放，
     * 用户看到的商品页与到手的东西不一致。价格同理已经有 {@link #amount}。
     */
    @Column(name = "plan_credits", nullable = false)
    private Integer planCredits;

    /** 权益快照：下单那一刻该套餐的有效天数。同 {@link #planCredits} 的理由 */
    @Column(name = "plan_days", nullable = false)
    private Integer planDays;

    /**
     * 下单金额（元）。
     *
     * <p>用 {@link BigDecimal} 而不是 double/float —— 金额的二进制浮点表示会引入
     * 无法精确比较的误差，而这里要拿它和支付宝回传的 total_amount 逐分比对。
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** 支付渠道。本次只有 ALIPAY_SANDBOX */
    @Column(name = "pay_channel", nullable = false, length = 20)
    private String payChannel;

    /**
     * 商户订单号，给支付宝的那个（同时也是回调反查本行的钥匙）。
     *
     * <p>生成规则 {@code MEM + yyyyMMddHHmmss + 6 位随机数}，见
     * {@code MembershipService.newOutTradeNo}。**订单一旦 CLOSED 就作废，不重用** ——
     * 用户重新下单会拿到新单号、新订单行，于是「一单一号一号一单」永远成立，
     * 唯一键与审计都干净（代价是列表里会留多条「已关闭」，这是有意的）。
     */
    @Column(name = "out_trade_no", nullable = false, length = 64)
    private String outTradeNo;

    /** 支付宝交易号，回调里带回来。未支付时为 null */
    @Column(name = "trade_no", length = 64)
    private String tradeNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MembershipOrderStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
