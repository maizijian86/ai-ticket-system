package com.aiticket.ticket.enums;

/**
 * 消耗 AI 额度的三种功能。
 *
 * <p>"每张工单上每个功能只能用一次"这条规则就是按这个枚举的粒度实现的 ——
 * 见 {@code ai_usage} 表的唯一键 {@code (ticket_id, feature, active_slot)}。
 *
 * <p>注意 ANALYSIS 是**一个**功能，尽管它一次调用会同时产出"技术建议"和
 * "任务拆解"两个 tab 的内容：前端那两个按钮打的是同一个接口、算一次消耗。
 * 拆成两个枚举值会让唯一键与用户看到的"一次操作"对不上。
 */
public enum AiFeature {
    /** 技术建议 + 任务拆解，POST /{id}/ai-analysis */
    ANALYSIS,
    /** 生成 PRD 文档，GET /{id}/download-prd */
    PRD,
    /** AI 对话。这一行是"解锁凭证"：存在即已解锁，后续多轮对话不再扣费。 */
    CHAT
}
