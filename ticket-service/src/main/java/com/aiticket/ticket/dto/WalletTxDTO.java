package com.aiticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水（给用户看的账本）。
 *
 * <p>{@link #type} 是后端枚举名，**前端负责翻译成人话**（充值/托管/退回/结算…）。
 * 不在后端翻译的原因：同一笔在"发布者视角"和"接单者视角"叫法不同
 * （{@code SETTLE_OUT} 对发布者是"支付工单款"，对收款方是"收到工单款"），
 * 前端可以按 {@code amount} 正负给出更准确的说法。
 *
 * <p>{@link #amount} 是**可用余额**的变动，{@link #frozenDelta} 是冻结额的变动。
 * 结算那条流水的 {@code amount} 是 0、金额在 {@code frozenDelta} 上 —— 这不是 bug：
 * 发布者付款时可用余额早就被冻结扣走了，结算只是把冻结划走。
 */
@Data
@Builder
public class WalletTxDTO {

    private Long id;

    /** RECHARGE / FREEZE / ESCROW_ADJUST / REFUND / SETTLE_OUT / SETTLE_IN */
    private String type;

    private BigDecimal amount;

    private BigDecimal frozenDelta;

    /** 本次变动后的可用余额 */
    private BigDecimal balanceAfter;

    /** TICKET / WALLET_ORDER */
    private String refType;

    private Long refId;

    private String remark;

    private LocalDateTime createdAt;
}
