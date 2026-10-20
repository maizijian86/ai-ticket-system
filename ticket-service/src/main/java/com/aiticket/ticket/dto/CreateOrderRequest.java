package com.aiticket.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 下单入参。只有套餐标识一个字段 —— 金额与权益一律由后端按 plan_code 查，不接受前端传价格。 */
@Data
public class CreateOrderRequest {

    /**
     * 套餐标识：{@code LITE} / {@code PRO} / {@code MAX}，取值以
     * {@code GET /api/v1/membership/plans} 为准（别在前端写死）。未知取值 → 400。
     *
     * <p>只认在售档位，**历史档位码不认** —— 已下线的档位码若能下单，
     * 一个陈旧的 localStorage 就能用旧价买到新权益（见 MembershipPlan.of / resolve 的分工）。
     */
    @NotBlank(message = "套餐标识不能为空")
    private String planCode;
}
