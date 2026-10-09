package com.aiticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 下单出参：把用户送到支付宝收银台去。
 *
 * <p><b>payUrl 由后端生成，前端只管打开。</b>前端不要自己去拼支付宝参数 ——
 * 那要把私钥拿到浏览器里做签名，等于把私钥交出去。
 *
 * <p>字符串的两种形态前端都要能处理（见 Membership.vue 的 goPay）：
 * 正常是 {@code https://…gateway.do?...&sign=…} 的 URL；个别 SDK 版本
 * {@code pageExecute} 返回的是自动提交的 form HTML，那时它以 {@code <form} 开头。
 */
@Data
@Builder
public class MembershipPayDTO {

    private Long orderId;

    private String outTradeNo;

    private BigDecimal amount;

    /** 收银台地址（或自动提交的 form HTML） */
    private String payUrl;
}
