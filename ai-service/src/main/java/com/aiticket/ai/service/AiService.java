package com.aiticket.ai.service;

import com.aiticket.ai.entity.KnowledgeBase;
import com.aiticket.ai.repository.KnowledgeBaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI基础服务 - 提供底层AI调用能力
 * 声明式AI服务由DeveloperAiService提供
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final RestTemplate restTemplate;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Value("${ai.minimax.api-key:}")
    private String apiKey;

    @Value("${ai.minimax.model:MiniMax-M2.7}")
    private String modelName;

    @Value("${ai.minimax.base-url:https://api.minimaxi.com}")
    private String baseUrl;

    public AiService(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(120000); // 120s read timeout for long generation
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 调用AI API（供需要直接调用的场景使用）
     */
    public String callAiApi(String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("AI_API_KEY is not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", modelName);
        body.put("max_tokens", 4000);
        body.put("messages", List.of(
                Map.of("role", "user", "content", systemPrompt + "\n\n" + userMessage)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = baseUrl + "/v1/messages";

        log.info("Calling AI API: {} with model: {}", url, modelName);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        return parseAiResponse(response.getBody());
    }

    private String parseAiResponse(String response) {
        Pattern textPattern = Pattern.compile("\"text\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher matcher = textPattern.matcher(response);

        if (matcher.find()) {
            String text = matcher.group(1);
            return text.replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        throw new RuntimeException("Failed to parse AI response: " + response);
    }

    // ==================== 知识库管理 ====================

    @Transactional
    public KnowledgeBase createKnowledge(String title, String content, String category, String sourceType, Long sourceId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTitle(title);
        kb.setContent(content);
        kb.setCategory(category);
        kb.setSourceType(sourceType);
        kb.setSourceId(sourceId);
        kb.setStatus("DRAFT");
        return knowledgeBaseRepository.save(kb);
    }

    @Transactional
    public KnowledgeBase publishKnowledge(Long id) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Knowledge not found"));
        kb.setStatus("PUBLISHED");
        return knowledgeBaseRepository.save(kb);
    }

    @Transactional
    public KnowledgeBase archiveKnowledge(Long id) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Knowledge not found"));
        kb.setStatus("ARCHIVED");
        return knowledgeBaseRepository.save(kb);
    }

    public List<KnowledgeSearchResult> retrieveRelevantKnowledge(String query, int limit) {
        try {
            List<KnowledgeBase> fulltextResults = knowledgeBaseRepository.findByKeyword(query, limit);

            if (fulltextResults.isEmpty()) {
                fulltextResults = knowledgeBaseRepository.findByKeywordFallback(query, limit);
            }

            List<KnowledgeSearchResult> searchResults = new ArrayList<>();
            int rank = 0;
            for (KnowledgeBase kb : fulltextResults) {
                if (rank >= limit) break;

                double relevanceScore = (kb.getHelpfulCount() + 1.0) / (kb.getViewCount() + 1.0);

                searchResults.add(new KnowledgeSearchResult(
                        kb.getId(),
                        kb.getTitle(),
                        kb.getContent(),
                        kb.getCategory(),
                        relevanceScore,
                        kb.getSourceType()
                ));

                knowledgeBaseRepository.incrementViewCount(kb.getId());
                rank++;
            }

            return searchResults;
        } catch (Exception e) {
            log.error("Knowledge retrieval failed", e);
            return Collections.emptyList();
        }
    }

    // ==================== 内部类 ====================

    @lombok.AllArgsConstructor
    @lombok.Data
    public static class KnowledgeSearchResult {
        private final Long id;
        private final String title;
        private final String content;
        private final String category;
        private final double relevanceScore;
        private final String sourceType;
    }

    private static class Pattern {
        private final java.util.regex.Pattern delegate;

        private Pattern(java.util.regex.Pattern delegate) {
            this.delegate = delegate;
        }

        static Pattern compile(String regex) {
            return new Pattern(java.util.regex.Pattern.compile(regex));
        }

        Matcher matcher(CharSequence input) {
            return new Matcher(delegate.matcher(input));
        }
    }

    private static class Matcher {
        private final java.util.regex.Matcher delegate;

        private Matcher(java.util.regex.Matcher delegate) {
            this.delegate = delegate;
        }

        boolean find() {
            return delegate.find();
        }

        String group(int group) {
            return delegate.group(group);
        }
    }
}
