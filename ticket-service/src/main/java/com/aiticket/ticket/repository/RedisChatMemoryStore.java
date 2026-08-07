package com.aiticket.ticket.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis实现的聊天记忆存储
 * 用于保存AI对话的上下文历史
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration TTL = Duration.ofDays(1); // 保留1天

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = stringRedisTemplate.opsForValue().get(memoryId.toString());
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> messageMaps = objectMapper.readValue(json, new TypeReference<>() {});
            List<ChatMessage> messages = new ArrayList<>();
            for (Map<String, Object> messageMap : messageMaps) {
                messages.add(convertToChatMessage(messageMap));
            }
            return messages;
        } catch (Exception e) {
            log.error("Failed to deserialize chat messages", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            List<Map<String, Object>> messageMaps = new ArrayList<>();
            for (ChatMessage message : messages) {
                messageMaps.add(convertToMap(message));
            }
            String json = objectMapper.writeValueAsString(messageMaps);
            stringRedisTemplate.opsForValue().set(memoryId.toString(), json, TTL);
        } catch (Exception e) {
            log.error("Failed to serialize chat messages", e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(memoryId.toString());
    }

    private Map<String, Object> convertToMap(ChatMessage message) {
        // 简化实现：只保存消息类型和内容
        return Map.of(
                "type", message.type().name(),
                "content", message.toString()
        );
    }

    private ChatMessage convertToChatMessage(Map<String, Object> map) {
        // 简化实现：根据类型重建消息
        String type = (String) map.get("type");
        String content = (String) map.get("content");

        // 这里简化处理，实际应该根据消息类型精确重建
        return new dev.langchain4j.data.message.SystemMessage(content);
    }
}
