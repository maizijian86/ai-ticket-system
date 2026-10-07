package com.aiticket.ticket.dto;

import com.aiticket.ticket.enums.AiFeatureState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单详情页 AI 卡片要的全部状态：账户还剩几次 + 三个功能各自能不能按。
 *
 * <p>一次给全，是因为前端渲染这个卡片需要同时知道"有没有额度"和"这个功能用没用过" ——
 * 分成两个接口会让遮罩文案在两次响应之间闪一下。
 */
@Data
@Builder
public class AiQuotaStatusDTO {

    /** 账户剩余可用次数（未过期批次之和）。非接单人时为 0，不暴露别人的账户信息 */
    private Integer balance;

    /**
     * 最近一次过期时间，只在**可用批次**里取。
     * 若从全部批次里取最小值，会员到期后会提示一个已经过去的日期。
     */
    private LocalDateTime nextExpireAt;

    /** 我是不是这张工单的接单人。false 时下面三个状态必为 NOT_HANDLER */
    private Boolean isHandler;

    private AiFeatureState analysis;

    private AiFeatureState prd;

    private AiFeatureState chat;
}
