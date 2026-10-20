package com.aiticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一笔会员订单。单笔查询（结果页轮询）与我的订单列表（个人主页）共用这一个形状 ——
 * 两处字段完全一致，分成两个 DTO 只会让字段漂移。
 */
@Data
@Builder
public class MembershipOrderDTO {

    private Long orderId;

    /** 完整展示，不截断 —— 用户对着它才能和支付宝的交易记录对上 */
    private String outTradeNo;

    /** **已规范化**：存量的 {@code MONTHLY_30}（Pro 的前身）在这里是 {@code PRO} */
    private String planCode;

    /**
     * 下单时那一档的显示名（"Pro 会员"）。
     *
     * <p>从 {@code planCode} 现算而不是存进订单表：档位的**名字**是可以改的文案，
     * 而订单表存的是钱和权益的**事实**（{@link #planCredits}/{@link #planDays}）。
     * 把"Pro 会员"这种文案也快照进库，改个名就要动历史单据。
     */
    private String planName;

    /** 权益**快照**：下单时的次数与天数，不是现在套餐配置里的值 */
    private Integer planCredits;

    private Integer planDays;

    private BigDecimal amount;

    /** PENDING / PAID / CLOSED，文案映射在前端的 utils/order-labels.ts */
    private String status;

    private LocalDateTime paidAt;

    /**
     * 开通后的会员到期日。**只在 PAID 的行上有值**，PENDING/CLOSED 为 null ——
     * 前端不要拿它算天数，那不是"还剩几天"，那只是"这笔单付了之后会到什么时候"。
     */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}
