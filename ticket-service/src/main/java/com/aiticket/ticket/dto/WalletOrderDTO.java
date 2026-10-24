package com.aiticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单（结果页轮询用、充值记录列表用）。
 *
 * <p>{@link #status} 只有三种：{@code PENDING} 待支付、{@code PAID} 已到账、
 * {@code CLOSED} 已关闭。前端据它决定"继续转圈 / 显示成功 / 显示失败"。
 */
@Data
@Builder
public class WalletOrderDTO {

    private Long id;

    private String outTradeNo;

    private BigDecimal amount;

    private String status;

    /** 支付宝交易号，未到账时为 null。展示在详情里方便对账，用户可以拿去核 */
    private String tradeNo;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}
