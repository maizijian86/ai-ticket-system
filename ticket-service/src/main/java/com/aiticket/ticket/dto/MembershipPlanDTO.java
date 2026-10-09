package com.aiticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 一个会员套餐。价格与权益文案的**唯一来源是后端**（{@code MembershipPlan} 枚举），
 * 前端从这里取，不写死 —— 改价不该要重新发版。
 */
@Data
@Builder
public class MembershipPlanDTO {

    /** 套餐标识，下单时原样回传 */
    private String planCode;

    private String name;

    private BigDecimal price;

    /** 本套餐发放的 AI 辅助开发次数 */
    private Integer credits;

    /** 有效期天数 */
    private Integer days;

    private String desc;
}
