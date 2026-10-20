package com.aiticket.ticket.dto;

/**
 * AI 对话历史里的一条，给详情页「对话」页签渲染气泡用。
 *
 * <p><b>不要和 {@link ChatMessageDTO} 搞混</b>：那个是发布者↔接单人的**私聊**
 * （落 {@code ticket_chat} 表、带发送人 id 和姓名、走 {@code GET /{id}/chat}）；
 * 这个是**人和模型**的对话，来源是 Redis 里的会话记忆，只有两个角色。
 *
 * <p>没有时间字段 —— 不是漏了，是 LangChain4j 的 {@code ChatMessage} 本身不带时间，
 * 存进 Redis 的 JSON 里也就没有。要时间就得另落一张表，本次不做（见 PUT 记录里的取舍）。
 *
 * <p>用 record 而不是 Lombok {@code @Data}：这是一份只读的投影，没有一处会去 set 它。
 *
 * @param role    {@link #ROLE_USER} 或 {@link #ROLE_AI}
 * @param content 已经剥掉 prompt 包装的正文（用户那侧见 {@code TicketService.cleanUserPrompt}）
 */
public record AiChatMessageDTO(String role, String content) {

    /** 接单人（工单的 handler） */
    public static final String ROLE_USER = "user";

    /** 模型 —— 前端按这个值决定气泡靠哪边、什么底色 */
    public static final String ROLE_AI = "ai";
}
