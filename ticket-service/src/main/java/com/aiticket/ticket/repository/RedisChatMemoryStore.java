package com.aiticket.ticket.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis实现的聊天记忆存储
 * 用于保存AI对话的上下文历史
 *
 * 编解码改用 LangChain4j 官方的 ChatMessageSerializer / ChatMessageDeserializer。
 *
 * 此前的实现用 Map{type, content} 手工存取，且反序列化时一律重建为
 * SystemMessage，导致多轮对话的角色（user / ai / system）全部丢失 ——
 * 模型收到的历史里每一句都变成"系统指令"，上下文完全错乱。
 *
 * Redis key 统一加 ai:chat:memory: 前缀，避免与 refresh_token 等其他键冲突。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    // 30 天。原来是 1 天，太短：一个工单的开发周期动辄跨周，第 2 天回来接着问，
    // 模型就完全不记得之前聊过什么。而且每次写入都会重置 TTL，30 天指的是
    // "最后一条消息之后 30 天"，活跃工单实际上不会过期。
    // 生命周期终点不是过期，是验收通过 —— 那时 TicketService.approveTicket
    // 会显式清理这份记忆。
    private static final Duration TTL = Duration.ofDays(30);
    private static final String KEY_PREFIX = "ai:chat:memory:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = stringRedisTemplate.opsForValue().get(key(memoryId));
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            log.error("反序列化对话历史失败: {}", memoryId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            stringRedisTemplate.opsForValue().set(key(memoryId), json, TTL);
        } catch (Exception e) {
            log.error("序列化对话历史失败: {}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(key(memoryId));
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
