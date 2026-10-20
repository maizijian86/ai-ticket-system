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

    /**
     * 本档位发布的工单是否在大厅优先曝光（目前只有 Max 为 true）。
     *
     * <p>由后端下发而不是前端按档位名写死：这是**权益**，不是文案 —— 前端把
     * "MAX 显示置顶图标"写死的话，以后调整哪一档带曝光就要改两个仓库。
     */
    private Boolean hallPromoted;

    private String desc;
}
