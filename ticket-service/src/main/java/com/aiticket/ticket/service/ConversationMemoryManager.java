package com.aiticket.ticket.service;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 会话记忆管理器 —— 统一管理 AI 调用的记忆作用域。
 *
 * 本类解决两个具体缺陷：
 *
 * 1. 【作用域错误】此前所有 AI 调用都以 creatorId（用户 ID）作为 @MemoryId，
 *    于是同一个用户的不同项目共用一份对话历史 —— 给 B 项目生成 PRD 时，
 *    模型能在上下文里看到 A 项目的 PRD。同时 PRD / 技术栈 / 任务拆解三套
 *    不同的 SystemMessage 也叠在同一条历史里互相冲突。
 *    改为按「工单 + 用途」隔离后，上述问题消失。
 *
 * 2. 【文档生成应有状态吗】PRD、技术栈建议、任务拆解都是「一次性生成」任务，
 *    正确语义是**无状态**：每次生成的结果不应受上一次生成影响。
 *    因此这三个作用域在每次调用前清空；只有 aiChat 这种真正的多轮对话
 *    才保留历史。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryManager {

    private static final String PRD = "ticket:prd:";
    private static final String TECH_STACK = "ticket:tech:";
    private static final String TASK_BREAKDOWN = "ticket:tasks:";
    private static final String CHAT = "ticket:chat:";

    private final ChatMemoryProvider chatMemoryProvider;

    /** PRD 生成作用域 —— 每次生成前清空，保持无状态 */
    public String prdScope(Long ticketId) {
        return reset(PRD + ticketId);
    }

    /** 技术栈分析作用域 —— 每次分析前清空，保持无状态 */
    public String techStackScope(Long ticketId) {
        return reset(TECH_STACK + ticketId);
    }

    /** 任务拆解作用域 —— 每次拆解前清空，保持无状态 */
    public String taskBreakdownScope(Long ticketId) {
        return reset(TASK_BREAKDOWN + ticketId);
    }

    /** AI 对话作用域 —— 保留历史，实现多轮上下文 */
    public String chatScope(Long ticketId) {
        return CHAT + ticketId;
    }

    /**
     * 销毁 AI 对话作用域 —— 验收通过、工单归档时调用。
     *
     * 与 chatScope 的区别：那个是"拿来用"（保留历史，多轮对话才有上下文），
     * 这个是"用完销毁"。**不能**把 chatScope 本身改成清空 —— 那会让每轮对话
     * 都从零开始，多轮记忆直接失效。
     */
    public void chatScopeAndClear(Long ticketId) {
        reset(CHAT + ticketId);
    }

    private String reset(String memoryId) {
        try {
            chatMemoryProvider.get(memoryId).clear();
        } catch (Exception e) {
            log.warn("清空 AI 记忆失败: {}", memoryId, e);
        }
        return memoryId;
    }
}
