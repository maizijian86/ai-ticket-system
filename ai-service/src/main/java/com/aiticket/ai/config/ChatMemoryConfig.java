package com.aiticket.ai.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ChatMemory配置 - 基于Redis实现用户级别上下文记忆
 * 每个用户独立的对话历史，支持多轮对话场景
 */
@Configuration
public class ChatMemoryConfig {

    private static final int MAX_MESSAGES = 50; // 每个用户最多保留50条消息
    private static final String REDIS_PREFIX = "ai:chat:memory:";
    private static final long EXPIRE_HOURS = 24; // 24小时过期

    private final StringRedisTemplate redisTemplate;

    public ChatMemoryConfig(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> new RedisChatMemory(memoryId, MAX_MESSAGES);
    }

    /**
     * 基于Redis的ChatMemory实现
     * 支持用户隔离：每个userId独立存储对话历史
     */
    private class RedisChatMemory implements ChatMemory {
        private final Object userId;
        private final int maxMessages;
        private final String redisKey;

        public RedisChatMemory(Object userId, int maxMessages) {
            this.userId = userId;
            this.maxMessages = maxMessages;
            this.redisKey = REDIS_PREFIX + userId;
        }

        @Override
        public Object id() {
            return userId;
        }

        @Override
        public void add(ChatMessage message) {
            // 序列化消息并存入Redis List
            String serialized = serializeMessage(message);
            redisTemplate.opsForList().rightPush(redisKey, serialized);
            redisTemplate.expire(redisKey, EXPIRE_HOURS, TimeUnit.HOURS);

            // 限制消息数量，移除最早的消息
            Long size = redisTemplate.opsForList().size(redisKey);
            if (size != null && size > maxMessages) {
                redisTemplate.opsForList().leftPop(redisKey, size - maxMessages);
            }
        }

        @Override
        public List<ChatMessage> messages() {
            List<String> serializedList = redisTemplate.opsForList().range(redisKey, 0, -1);
            if (serializedList == null) {
                return List.of();
            }
            return serializedList.stream()
                    .map(this::deserializeMessage)
                    .filter(msg -> msg != null)
                    .toList();
        }

        @Override
        public void clear() {
            redisTemplate.delete(redisKey);
        }

        private String serializeMessage(ChatMessage message) {
            // 简单序列化：类型|内容
            String type = message.getClass().getSimpleName();
            String content = "";
            if (message instanceof dev.langchain4j.data.message.UserMessage userMsg) {
                content = userMsg.singleText();
            } else if (message instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
                content = aiMsg.text() != null ? aiMsg.text() : "";
            } else if (message instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
                content = sysMsg.text();
            }
            return type + "|" + content.replace("\\", "\\\\").replace("|", "\\|");
        }

        private ChatMessage deserializeMessage(String serialized) {
            String[] parts = serialized.split("\\|", 2);
            if (parts.length != 2) return null;

            String type = parts[0];
            String content = parts[1].replace("\\|", "|").replace("\\\\", "\\");

            return switch (type) {
                case "UserMessage" -> dev.langchain4j.data.message.UserMessage.from(content);
                case "AiMessage" -> dev.langchain4j.data.message.AiMessage.from(content);
                case "SystemMessage" -> dev.langchain4j.data.message.SystemMessage.from(content);
                default -> null;
            };
        }
    }
}
