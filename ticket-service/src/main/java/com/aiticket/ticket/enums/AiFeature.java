package com.aiticket.ticket.enums;

/**
 * AI 额度记账用的功能标识。
 *
 * <p><b>一张工单只花两次</b>：{@link #ANALYSIS} 与 {@link #CHAT} 合并成**同一个槽位**
 * （谁先用谁扣，另一个免费），{@link #PRD} 独立一次。粒度的权威在 {@code ai_usage}
 * 表的唯一键 {@code (ticket_id, feature, active_slot)}。
 *
 * <p>合并的这一对**共用 ANALYSIS 这个 feature 记账**（见 {@code AiQuotaService} 的
 * {@code groupOf}）：两个功能各写一行的话，唯一键就变成"每工单每功能一次"＝ 三次，
 * 与"总共两次"对不上，而且并发下两个插入命中不同的唯一键、谁也挡不住谁。
 * 写在同一个 feature 上，数据库才能真的保证这一对只被扣一次。
 *
 * <p>注意 ANALYSIS 之所以还叫这个名字，只是因为它一次调用会同时产出"技术建议"和
 * "任务拆解"两个 tab 的内容 —— 名字取窄了，但它现在是"分析与对话"这一对的记账位。
 * 改枚举常量名要同步改 {@code ai_usage.feature} 里的存量值（VARCHAR，见 db/schema.sql），
 * 不值得，于是在这里说清楚。
 */
public enum AiFeature {
    /**
     * 分析与对话的合并槽位。
     *
     * <p>写：POST /{id}/ai-analysis 与 POST /{id}/ai-chat 都记在这一行上。
     * 读：这一行是 SUCCESS，就说明"分析与对话"这一次已经付过；两个功能都能用，
     * 谁还没产出过内容谁就能再跑一次，不再扣费。
     */
    ANALYSIS,
    /** 生成 PRD 文档，GET /{id}/download-prd。独立一次，与另外两个互不影响。 */
    PRD,
    /**
     * AI 对话 —— <b>历史值，新代码不再写入</b>。
     *
     * <p>合并之前对话是独立的一格，库里留下的那些 CHAT 行（唯一键决定了每票至多一行）
     * 仍然要认：读取时它与 ANALYSIS 同属一个槽位，任何一边是 SUCCESS 都算这一对用过了。
     * 保留这个常量就是为了读得懂那些行，别在别处再用它去记账。
     */
    CHAT
}
