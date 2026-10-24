package com.aiticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值下单出参：把用户送到支付宝收银台。
 *
 * <p>与 {@code MembershipPayDTO} 结构一致，故意分开而不是复用 ——
 * 两个业务的金额来源不同（充值是用户自填，会员是套餐定价），
 * 以后一边加字段（比如充值加发票信息）不会牵连另一边。
 *
 * <p>{@link #payUrl} 由后端签发，前端只管打开。前端不要自己拼支付宝参数 ——
 * 那要把私钥拿到浏览器里做签名，等于把私钥交出去。
 *
 * <p>两种形态前端都要能处理：正常是 {@code https://…gateway.do?...} 的 URL；
 * 个别 SDK 版本 {@code pageExecute} 返回自动提交的 form HTML，那时它以 {@code <form} 开头。
 */
@Data
@Builder
public class WalletPayDTO {

    private Long orderId;

    private String outTradeNo;

    private BigDecimal amount;

    /** 收银台地址（或自动提交的 form HTML） */
    private String payUrl;
}
