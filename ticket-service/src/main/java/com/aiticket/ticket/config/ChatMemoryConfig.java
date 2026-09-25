package com.aiticket.ticket.config;

import com.aiticket.ticket.repository.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置
 *
 * 修复背景：TicketAiService 的 5 个方法全部标注了 @MemoryId，但本服务此前
 * 没有任何 ChatMemoryProvider bean（仓库里的 RedisChatMemoryStore 是
 * ChatMemoryStore，属于存储层，且当时未被任何代码引用）。LangChain4j 在
 * 使用 @MemoryId 时要求必须提供 ChatMemoryProvider，缺失会导致 AI 调用失败。
 *
 * 记忆的「作用域划分」由 ConversationMemoryManager 负责，这里只提供工厂。
 */
@Configuration
public class ChatMemoryConfig {

    /** 单个会话保留的最大消息条数 */
    private static final int MAX_MESSAGES = 20;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(RedisChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(store)
                .build();
    }
}
