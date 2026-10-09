package com.aiticket.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 下单入参。只有套餐标识一个字段 —— 金额与权益一律由后端按 plan_code 查，不接受前端传价格。 */
@Data
public class CreateOrderRequest {

    /** 套餐标识，如 MONTHLY_30。未知取值 → 400 */
    @NotBlank(message = "套餐标识不能为空")
    private String planCode;
}
