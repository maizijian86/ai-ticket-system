package com.aiticket.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 充值请求体。只有一个金额。
 *
 * <p>这里只做 {@code @NotNull} —— **范围与整数校验放在服务层**
 * （{@code WalletService.requireValidAmount}），因为那套规则工单预算也在用，
 * 放两处迟早会不一致。而且服务层的报错能说清"是小数问题还是范围问题"，
 * 注解上的 message 做不到这件事。
 */
@Data
public class RechargeRequest {

    @NotNull(message = "请填写充值金额")
    private BigDecimal amount;
}
