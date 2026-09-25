package com.aiticket.ticket.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(0.7)
                // 生成一篇完整 PRD（含数据库表、接口清单、里程碑）要 1~3 分钟，
                // 而 langchain4j-open-ai 的默认读超时只有 60 秒。默认配置下这次
                // 实测出现了 attempt 1/3、2/3 连续 InterruptedIOException: timeout，
                // 靠第 3 次重试才成功 —— 等于"每次生成都大概率失败"。
                // 这里把单次超时放宽到 5 分钟，让它一次跑完，而不是靠重试赌。
                .timeout(Duration.ofMinutes(5))
                // 重试保留 1 次作为兜底，但不再默认 3 次：超时场景下重试只会
                // 把用户等待时间乘以 3，且每次重试都要重新计费。
                .maxRetries(1)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
