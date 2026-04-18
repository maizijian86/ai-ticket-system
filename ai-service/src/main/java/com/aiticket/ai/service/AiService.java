package com.aiticket.ai.service;

import com.aiticket.ai.dto.*;
import com.aiticket.ai.entity.AiClassificationLog;
import com.aiticket.ai.entity.KnowledgeBase;
import com.aiticket.ai.repository.AiClassificationLogRepository;
import com.aiticket.ai.repository.KnowledgeBaseRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiService {

    // Category keywords for rule-based classification fallback
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "BUG", List.of("崩溃", "报错", "失败", "异常", "bug", "error", "crash", "无法使用", "不能点击", "页面空白", "500", "404", "无反应"),
            "CONSULT", List.of("如何", "怎么", "请问", "咨询", "问一下", "问一下", "使用方法", "功能", "在哪里", "是什么"),
            "COMPLAINT", List.of("投诉", "太差", "不满意", "垃圾", "愤怒", "非常失望", "态度", "不专业"),
            "SUGGESTION", List.of("建议", "希望", "可以增加", "改进", "优化", "希望能有", "期待")
    );

    // Priority keywords
    private static final Map<String, List<String>> PRIORITY_KEYWORDS = Map.of(
            "P0", List.of("崩溃", "完全无法使用", "核心功能", "紧急", "critical", "urgent", "系统宕机", "数据丢失"),
            "P1", List.of("严重影响", "多人", "主要流程", "功能故障", "高优先级"),
            "P2", List.of("部分功能", "单人", "功能异常", "使用不便"),
            "P3", List.of("轻微", "建议", "咨询", "体验", "cosmetic")
    );

    private final RestTemplate restTemplate;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AiClassificationLogRepository classificationLogRepository;

    @Value("${ai.minimax.api-key:}")
    private String apiKey;

    @Value("${ai.minimax.model:MiniMax-M2.7}")
    private String modelName;

    @Value("${ai.minimax.base-url:https://api.minimaxi.com}")
    private String baseUrl;

    public AiService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            AiClassificationLogRepository classificationLogRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.classificationLogRepository = classificationLogRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 10s connection timeout
        factory.setReadTimeout(60000);     // 60s read timeout
        this.restTemplate = new RestTemplate(factory);
    }

    public ClassifyResponse classify(String content) {
        log.info("AI classifying content, length: {}", content.length());

        try {
            MiniMaxRequest request = MiniMaxRequest.builder()
                    .model(modelName)
                    .maxTokens(1000)
                    .messages(List.of(
                            Map.of("role", "user", "content", buildClassificationPrompt(content))
                    ))
                    .build();

            String response = callMiniMax(request);

            ClassifyResult result = parseClassificationResponse(response);
            if (result != null && result.isValid()) {
                logClassification(result.category(), result.confidence());

                return ClassifyResponse.builder()
                        .category(result.category())
                        .confidence(result.confidence())
                        .reasoning(result.reasoning())
                        .build();
            }
        } catch (Exception e) {
            log.error("LLM classification failed, falling back to rule-based", e);
        }

        return ruleBasedClassification(content);
    }

    public PriorityResponse evaluatePriority(String content, String urgency) {
        log.info("AI evaluating priority for content, length: {}", content.length());

        try {
            MiniMaxRequest request = MiniMaxRequest.builder()
                    .model(modelName)
                    .maxTokens(1000)
                    .messages(List.of(
                            Map.of("role", "user", "content", buildPriorityPrompt(content, urgency))
                    ))
                    .build();

            String response = callMiniMax(request);

            PriorityResult result = parsePriorityResponse(response);
            if (result != null && result.isValid()) {
                return PriorityResponse.builder()
                        .priority(result.priority())
                        .score(result.score())
                        .factors(result.factors())
                        .build();
            }
        } catch (Exception e) {
            log.error("LLM priority evaluation failed, falling back to rule-based", e);
        }

        return ruleBasedPriority(content, urgency);
    }

    public RecommendHandlerResponse recommendHandler(String content, String category) {
        log.info("AI recommending handler for content, category: {}", category);

        try {
            MiniMaxRequest request = MiniMaxRequest.builder()
                    .model(modelName)
                    .maxTokens(500)
                    .messages(List.of(
                            Map.of("role", "user", "content", buildRecommendHandlerPrompt(content, category))
                    ))
                    .build();

            String response = callMiniMax(request);

            return parseRecommendHandlerResponse(response);
        } catch (Exception e) {
            log.error("LLM handler recommendation failed", e);
            return buildDefaultRecommendation();
        }
    }

    public SummaryResponse generateSummary(String content) {
        log.info("AI generating summary for content, length: {}", content.length());

        try {
            MiniMaxRequest request = MiniMaxRequest.builder()
                    .model(modelName)
                    .maxTokens(1500)
                    .messages(List.of(
                            Map.of("role", "user", "content", buildSummaryPrompt(content))
                    ))
                    .build();

            String response = callMiniMax(request);

            return parseSummaryResponse(response);
        } catch (Exception e) {
            log.error("LLM summary generation failed", e);
            return buildDefaultSummary(content);
        }
    }

    public GenerateTitleResponse generateTitle(String content) {
        log.info("AI generating title for content, length: {}", content.length());

        try {
            MiniMaxRequest request = MiniMaxRequest.builder()
                    .model(modelName)
                    .maxTokens(200)
                    .messages(List.of(
                            Map.of("role", "user", "content", buildGenerateTitlePrompt(content))
                    ))
                    .build();

            String response = callMiniMax(request);

            return GenerateTitleResponse.builder()
                    .title(response.trim())
                    .build();
        } catch (Exception e) {
            log.error("LLM title generation failed", e);
            return GenerateTitleResponse.builder()
                    .title(content.length() > 50 ? content.substring(0, 50) : content)
                    .build();
        }
    }

    public SuggestReplyResponse suggestReply(String question, Long ticketId, String context) {
        log.info("AI suggesting reply for question, length: {}", question.length());

        try {
            List<KnowledgeSearchResult> relevantDocs = retrieveRelevantKnowledge(question);

            MiniMaxRequest request = MiniMaxRequest.builder()
                    .model(modelName)
                    .maxTokens(2000)
                    .messages(List.of(
                            Map.of("role", "user", "content", buildSuggestReplyPrompt(question, relevantDocs, context))
                    ))
                    .build();

            String response = callMiniMax(request);

            return SuggestReplyResponse.builder()
                    .suggestedReply(response.trim())
                    .references(convertToReferences(relevantDocs))
                    .build();
        } catch (Exception e) {
            log.error("LLM reply suggestion failed", e);
            return SuggestReplyResponse.builder()
                    .suggestedReply("感谢您的反馈，我们正在处理您的问题，请稍候。")
                    .references(Collections.emptyList())
                    .build();
        }
    }

    public RecommendPriceResponse recommendPrice(String content, String category, String priority, String urgency) {
        log.info("AI recommending price for content, category: {}, priority: {}", category, priority);

        try {
            MiniMaxRequest request = MiniMaxRequest.builder()
                    .model(modelName)
                    .maxTokens(500)
                    .messages(List.of(
                            Map.of("role", "user", "content", buildRecommendPricePrompt(content, category, priority, urgency))
                    ))
                    .build();

            String response = callMiniMax(request);

            return parseRecommendPriceResponse(response);
        } catch (Exception e) {
            log.error("LLM price recommendation failed, using default", e);
            return buildDefaultPriceRecommendation();
        }
    }

    public EmbeddingResponse generateEmbedding(String text) {
        // MySQL doesn't support vector type, return empty embedding
        // In production, could call MiniMax embedding API if needed
        return EmbeddingResponse.builder()
                .embedding(new float[0])
                .dimension(0)
                .build();
    }

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

    // ==================== Private Helper Methods ====================

    private String callMiniMax(MiniMaxRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("MINIMAX_API_KEY is not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("max_tokens", request.getMaxTokens());
        body.put("messages", request.getMessages());

        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = baseUrl + "/v1/text/chatcompletion_v2";

        log.info("Calling MiniMax API: {} with model: {}", url, request.getModel());

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        return parseMiniMaxResponse(response.getBody());
    }

    private String parseMiniMaxResponse(String response) {
        // Parse OpenAI-style response: {"choices": [{"message": {"content": "..."}}]}
        Pattern textPattern = Pattern.compile("\"content\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher matcher = textPattern.matcher(response);

        if (matcher.find()) {
            String text = matcher.group(1);
            return text.replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        throw new RuntimeException("Failed to parse MiniMax response: " + response);
    }

    private List<KnowledgeSearchResult> retrieveRelevantKnowledge(String query) {
        return retrieveRelevantKnowledge(query, 5);
    }

    private String buildClassificationPrompt(String content) {
        return String.format("""
                请分析以下工单内容，判断其类别。

                类别定义：
                - BUG（缺陷）: 系统故障、功能异常、报错、无法使用
                - CONSULT（咨询）: 问题咨询、功能询问、如何使用
                - COMPLAINT（投诉）: 用户不满、投诉反馈
                - SUGGESTION（建议）: 改进建议、新需求
                - OTHER（其他）: 不属于以上类别

                请返回JSON格式：
                {"category": "类别", "confidence": 置信度(0-100), "reasoning": "判断理由"}

                工单内容：
                %s
                """, content);
    }

    private String buildPriorityPrompt(String content, String urgency) {
        return String.format("""
                请分析以下工单内容，评估其优先级。

                优先级定义：
                - P0（紧急）: 核心功能不可用、系统崩溃、紧急故障
                - P1（高）: 影响主要流程、非核心功能故障、影响多人
                - P2（中）: 影响部分功能、单用户问题、功能瑕疵
                - P3（低）: 轻微问题、咨询、建议、体验问题

                用户选择的紧急程度：%s

                请返回JSON格式：
                {"priority": "优先级", "score": 评分(0-100), "factors": "因素分析"}

                工单内容：
                %s
                """, urgency != null ? urgency : "NORMAL", content);
    }

    private String buildRecommendHandlerPrompt(String content, String category) {
        return String.format("""
                请分析以下工单内容，推荐最适合处理的技能方向。

                工单类别：%s

                请只返回一个技能标签（如：java, mysql, redis, kafka, spring, redis等），
                如果不确定，返回"general"。

                只返回技能标签，不要其他内容。
                工单内容：
                %s
                """, category != null ? category : "未分类", content);
    }

    private String buildSummaryPrompt(String content) {
        return String.format("""
                请为以下工单生成简洁摘要，提取关键信息。

                请返回JSON格式：
                {
                    "summary": "问题概述（1-2句话）",
                    "keyInfo": "关键信息（涉及系统、操作步骤、错误信息）",
                    "solution": "解决方案（如有）"
                }

                工单内容：
                %s
                """, content);
    }

    private String buildGenerateTitlePrompt(String content) {
        return String.format("""
                你是一个工单标题生成器。请仔细阅读下面的用户描述，用5-15个字提炼出问题核心，生成一个简洁的标题。

                规则：
                - 标题必须5-15个中文字符
                - 只返回标题，不要解释、不要引号、不要任何其他内容
                - 不要照搬描述，要提炼压缩
                - 不要加"工单"、"问题"等前缀
                - 格式：直接输出标题

                示例：
                描述：我的电脑开机后蓝屏，显示"KERNEL_DATA_INPAGE_ERROR"，重启多次都是一样
                标题：电脑蓝屏无法启动

                描述：想问一下这个工单系统怎么创建新用户，是需要管理员权限吗
                标题：如何创建新用户

                描述：系统登录页面打不开，一直转圈加载
                标题：登录页面无法加载

                工单描述：
                %s
                """, content);
    }

    private String buildSuggestReplyPrompt(String question, List<KnowledgeSearchResult> knowledge, String context) {
        StringBuilder kbContext = new StringBuilder();
        if (knowledge != null && !knowledge.isEmpty()) {
            kbContext.append("参考知识库：\n");
            for (int i = 0; i < Math.min(knowledge.size(), 3); i++) {
                KnowledgeSearchResult kb = knowledge.get(i);
                kbContext.append(String.format("%d. [%s] %s\n内容：%s\n\n",
                        i + 1, kb.getCategory(), kb.getTitle(), kb.getContent()));
            }
        }

        return String.format("""
                %s

                用户问题：%s

                %s

                请基于上述参考信息，生成一个专业、友好的回复建议。
                如果参考信息中有相关解决方案，请结合实际给出回复。
                """, kbContext.toString(), question, context != null ? "对话上下文：" + context : "");
    }

    private String buildRecommendPricePrompt(String content, String category, String priority, String urgency) {
        return String.format("""
                请根据以下工单内容，推荐一个合理的价格。

                工单类别：%s
                工单优先级：%s
                用户紧急程度：%s

                定价参考：
                - BUG类问题通常在50-200元
                - CONSULT咨询类通常在10-50元
                - SUGGESTION建议类通常在20-100元
                - COMPLAINT投诉类通常在30-150元
                - 优先级越高价格越高
                - 紧急程度越高价格越高

                请返回JSON格式：
                {"suggestedPrice": 推荐价格(数字), "reasoning": "定价理由", "priceRange": "价格区间字符串"}

                工单内容：
                %s
                """, category != null ? category : "未分类",
                   priority != null ? priority : "P2",
                   urgency != null ? urgency : "NORMAL",
                   content);
    }

    private RecommendPriceResponse parseRecommendPriceResponse(String response) {
        try {
            Pattern pricePattern = Pattern.compile("\"suggestedPrice\"\\s*:\\s*([\\d.]+)");
            Pattern reasoningPattern = Pattern.compile("\"reasoning\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
            Pattern rangePattern = Pattern.compile("\"priceRange\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");

            Matcher priceMatcher = pricePattern.matcher(response);
            Matcher reasoningMatcher = reasoningPattern.matcher(response);
            Matcher rangeMatcher = rangePattern.matcher(response);

            if (priceMatcher.find()) {
                BigDecimal price = new BigDecimal(priceMatcher.group(1));
                String reasoning = reasoningMatcher.find() ? unescapeJsonString(reasoningMatcher.group(1)) : "";
                String range = rangeMatcher.find() ? unescapeJsonString(rangeMatcher.group(1)) : "";

                return RecommendPriceResponse.builder()
                        .suggestedPrice(price)
                        .reasoning(reasoning)
                        .priceRange(range)
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to parse price recommendation response: {}", response, e);
        }
        return buildDefaultPriceRecommendation();
    }

    private RecommendPriceResponse buildDefaultPriceRecommendation() {
        return RecommendPriceResponse.builder()
                .suggestedPrice(new BigDecimal("50.00"))
                .reasoning("Based on default pricing")
                .priceRange("30-100")
                .build();
    }

    private ClassifyResult parseClassificationResponse(String response) {
        try {
            Pattern categoryPattern = Pattern.compile("\"category\"\\s*:\\s*\"([^\"]+)\"");
            Pattern confidencePattern = Pattern.compile("\"confidence\"\\s*:\\s*([\\d.]+)");
            Pattern reasoningPattern = Pattern.compile("\"reasoning\"\\s*:\\s*\"([^\"]+)\"");

            Matcher categoryMatcher = categoryPattern.matcher(response);
            Matcher confidenceMatcher = confidencePattern.matcher(response);
            Matcher reasoningMatcher = reasoningPattern.matcher(response);

            if (categoryMatcher.find() && confidenceMatcher.find()) {
                String category = categoryMatcher.group(1).toUpperCase();
                if (!List.of("BUG", "CONSULT", "COMPLAINT", "SUGGESTION", "OTHER").contains(category)) {
                    category = "OTHER";
                }

                String reasoning = reasoningMatcher.find() ? reasoningMatcher.group(1) : "";
                double confidence = Double.parseDouble(confidenceMatcher.group(1));

                return new ClassifyResult(category, BigDecimal.valueOf(confidence), reasoning);
            }
        } catch (Exception e) {
            log.error("Failed to parse classification response: {}", response, e);
        }
        return null;
    }

    private PriorityResult parsePriorityResponse(String response) {
        try {
            Pattern priorityPattern = Pattern.compile("\"priority\"\\s*:\\s*\"(P[0-3])\"");
            Pattern scorePattern = Pattern.compile("\"score\"\\s*:\\s*([\\d.]+)");
            Pattern factorsPattern = Pattern.compile("\"factors\"\\s*:\\s*\"([^\"]+)\"");

            Matcher priorityMatcher = priorityPattern.matcher(response);
            Matcher scoreMatcher = scorePattern.matcher(response);
            Matcher factorsMatcher = factorsPattern.matcher(response);

            if (priorityMatcher.find() && scoreMatcher.find()) {
                String priority = priorityMatcher.group(1);
                double score = Double.parseDouble(scoreMatcher.group(1));
                String factors = factorsMatcher.find() ? factorsMatcher.group(1) : "";

                return new PriorityResult(priority, BigDecimal.valueOf(score), factors);
            }
        } catch (Exception e) {
            log.error("Failed to parse priority response: {}", response, e);
        }
        return null;
    }

    private RecommendHandlerResponse parseRecommendHandlerResponse(String response) {
        try {
            String skill = response.trim();
            return RecommendHandlerResponse.builder()
                    .handlerName(skill)
                    .reason("Based on content analysis: " + skill)
                    .matchScore(0.75)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse recommend handler response", e);
            return buildDefaultRecommendation();
        }
    }

    private SummaryResponse parseSummaryResponse(String response) {
        try {
            Pattern summaryPattern = Pattern.compile("\"summary\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
            Pattern keyInfoPattern = Pattern.compile("\"keyInfo\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
            Pattern solutionPattern = Pattern.compile("\"solution\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");

            Matcher summaryMatcher = summaryPattern.matcher(response);
            Matcher keyInfoMatcher = keyInfoPattern.matcher(response);
            Matcher solutionMatcher = solutionPattern.matcher(response);

            if (summaryMatcher.find()) {
                String summary = unescapeJsonString(summaryMatcher.group(1));
                String keyInfo = keyInfoMatcher.find() ? unescapeJsonString(keyInfoMatcher.group(1)) : "";
                String solution = solutionMatcher.find() ? unescapeJsonString(solutionMatcher.group(1)) : "";

                return SummaryResponse.builder()
                        .summary(summary)
                        .keyInfo(keyInfo)
                        .solution(solution)
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to parse summary response: {}", response, e);
        }
        return buildDefaultSummary(response);
    }

    private String unescapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private ClassifyResponse ruleBasedClassification(String content) {
        String lowerContent = content.toLowerCase();
        Map<String, Integer> matchCounts = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            int count = 0;
            for (String keyword : entry.getValue()) {
                if (lowerContent.contains(keyword.toLowerCase())) {
                    count++;
                }
            }
            matchCounts.put(entry.getKey(), count);
        }

        String bestCategory = "OTHER";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : matchCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                bestCategory = entry.getKey();
            }
        }

        double confidence = Math.min(85.0, 50.0 + maxCount * 10);

        return ClassifyResponse.builder()
                .category(bestCategory)
                .confidence(BigDecimal.valueOf(confidence))
                .reasoning("Rule-based classification")
                .build();
    }

    private PriorityResponse ruleBasedPriority(String content, String urgency) {
        String lowerContent = content.toLowerCase();
        Map<String, Integer> matchCounts = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : PRIORITY_KEYWORDS.entrySet()) {
            int count = 0;
            for (String keyword : entry.getValue()) {
                if (lowerContent.contains(keyword.toLowerCase())) {
                    count++;
                }
            }
            matchCounts.put(entry.getKey(), count);
        }

        String bestPriority = "P2";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : matchCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                bestPriority = entry.getKey();
            }
        }

        double urgencyFactor = switch (urgency != null ? urgency.toUpperCase() : "NORMAL") {
            case "CRITICAL" -> 30;
            case "HIGH" -> 20;
            case "LOW" -> -10;
            default -> 0;
        };

        double score = Math.min(100, 50 + maxCount * 15 + urgencyFactor);

        return PriorityResponse.builder()
                .priority(bestPriority)
                .score(BigDecimal.valueOf(score))
                .factors("{\"keywordMatches\":" + maxCount + ",\"urgencyFactor\":" + urgencyFactor + "}")
                .build();
    }

    private RecommendHandlerResponse buildDefaultRecommendation() {
        return RecommendHandlerResponse.builder()
                .handlerName("general")
                .reason("No specific skill matched, assigning to general handler")
                .matchScore(0.3)
                .build();
    }

    private SummaryResponse buildDefaultSummary(String content) {
        String summary = content.length() > 100 ? content.substring(0, 100) + "..." : content;
        return SummaryResponse.builder()
                .summary(summary)
                .keyInfo("")
                .solution("")
                .build();
    }

    private List<Map<String, Object>> convertToReferences(List<KnowledgeSearchResult> results) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (KnowledgeSearchResult r : results) {
            refs.add(Map.of(
                    "id", r.getId(),
                    "title", r.getTitle(),
                    "category", r.getCategory() != null ? r.getCategory() : "",
                    "similarity", r.getRelevanceScore()
            ));
        }
        return refs;
    }

    private void logClassification(String category, BigDecimal confidence) {
        try {
            AiClassificationLog logEntry = new AiClassificationLog();
            logEntry.setTicketId(0L);
            logEntry.setAiCategory(category);
            logEntry.setAiConfidence(confidence);
            classificationLogRepository.save(logEntry);
        } catch (Exception e) {
            AiService.log.error("Failed to log classification", e);
        }
    }

    // Inner classes
    @Data
    @lombok.Builder
    private static class MiniMaxRequest {
        private String model;
        private int maxTokens;
        private List<Map<String, Object>> messages;
        private Double temperature;
    }

    private record ClassifyResult(String category, BigDecimal confidence, String reasoning) {
        boolean isValid() {
            return category != null && confidence != null &&
                    List.of("BUG", "CONSULT", "COMPLAINT", "SUGGESTION", "OTHER").contains(category);
        }
    }

    private record PriorityResult(String priority, BigDecimal score, String factors) {
        boolean isValid() {
            return priority != null && score != null &&
                    List.of("P0", "P1", "P2", "P3").contains(priority);
        }
    }

    @Data
    @lombok.AllArgsConstructor
    public static class KnowledgeSearchResult {
        private final Long id;
        private final String title;
        private final String content;
        private final String category;
        private final double relevanceScore;
        private final String sourceType;
    }
}
