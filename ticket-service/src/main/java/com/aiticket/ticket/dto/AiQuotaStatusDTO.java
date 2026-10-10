package com.aiticket.ticket.dto;

import com.aiticket.ticket.enums.AiFeatureState;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单详情页 AI 卡片要的全部状态：账户还剩几次 + 两个槽位各自能不能按。
 *
 * <p>槽位不是功能：{@code analysis} 与 {@code chat} 是**同一格**（分析与对话共用一次
 * 消耗），两者必然相等，前端拿哪一个都一样。分开返回只是为了让两处的渲染代码各读各的
 * 字段、不必互相知道对方的存在。
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

    /**
     * "AI 分析与对话"这一格的状态。
     *
     * <p>注意 {@code USED} 的含义被合并改宽了：它只说明**这次额度已经付过**，不再等于
     * "这两个功能都不能用了"。付过的那一格对两个功能都开放 —— 谁还没产出过内容，
     * 谁就能免费再跑一次。前端据此决定按钮能不能按。
     */
    private AiFeatureState analysis;

    /** PRD 独立一格，与上面两项互不影响。 */
    private AiFeatureState prd;

    /** 与 {@link #analysis} 同一个值（见类注释）。 */
    private AiFeatureState chat;
}
