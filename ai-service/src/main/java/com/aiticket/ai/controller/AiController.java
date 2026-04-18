package com.aiticket.ai.controller;

import com.aiticket.ai.dto.*;
import com.aiticket.ai.entity.KnowledgeBase;
import com.aiticket.ai.service.AiService;
import com.aiticket.common.dto.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/classify")
    public Result<ClassifyResponse> classify(@Valid @RequestBody ClassifyRequest request) {
        return Result.success(aiService.classify(request.getContent()));
    }

    @PostMapping("/priority")
    public Result<PriorityResponse> evaluatePriority(@Valid @RequestBody PriorityEvaluateRequest request) {
        return Result.success(aiService.evaluatePriority(
                request.getContent(),
                request.getUrgency()
        ));
    }

    @PostMapping("/recommend-handler")
    public Result<RecommendHandlerResponse> recommendHandler(@Valid @RequestBody RecommendHandlerRequest request) {
        return Result.success(aiService.recommendHandler(
                request.getContent(),
                request.getCategory()
        ));
    }

    @PostMapping("/summary")
    public Result<SummaryResponse> generateSummary(@Valid @RequestBody SummaryRequest request) {
        return Result.success(aiService.generateSummary(request.getContent()));
    }

    @PostMapping("/generate-title")
    public Result<GenerateTitleResponse> generateTitle(@Valid @RequestBody GenerateTitleRequest request) {
        return Result.success(aiService.generateTitle(request.getContent()));
    }

    @PostMapping("/suggest-reply")
    public Result<SuggestReplyResponse> suggestReply(@Valid @RequestBody SuggestReplyRequest request) {
        return Result.success(aiService.suggestReply(
                request.getQuestion(),
                request.getTicketId(),
                request.getContext()
        ));
    }

    @PostMapping("/recommend-price")
    public Result<RecommendPriceResponse> recommendPrice(@Valid @RequestBody RecommendPriceRequest request) {
        return Result.success(aiService.recommendPrice(
                request.getContent(),
                request.getCategory(),
                request.getPriority(),
                request.getUrgency()
        ));
    }

    @PostMapping("/embedding")
    public Result<EmbeddingResponse> generateEmbedding(@Valid @RequestBody EmbeddingRequest request) {
        return Result.success(aiService.generateEmbedding(request.getText()));
    }

    // Knowledge Base Management
    @PostMapping("/knowledge")
    public Result<KnowledgeBase> createKnowledge(@RequestBody CreateKnowledgeRequest request) {
        KnowledgeBase kb = aiService.createKnowledge(
                request.getTitle(),
                request.getContent(),
                request.getCategory(),
                request.getSourceType(),
                request.getSourceId()
        );
        return Result.success(kb);
    }

    @PutMapping("/knowledge/{id}/publish")
    public Result<KnowledgeBase> publishKnowledge(@PathVariable Long id) {
        return Result.success(aiService.publishKnowledge(id));
    }

    @PutMapping("/knowledge/{id}/archive")
    public Result<KnowledgeBase> archiveKnowledge(@PathVariable Long id) {
        return Result.success(aiService.archiveKnowledge(id));
    }

    @GetMapping("/knowledge/search")
    public Result<List<AiService.KnowledgeSearchResult>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(aiService.retrieveRelevantKnowledge(query, limit));
    }

    // DTO for knowledge creation
    @lombok.Data
    public static class CreateKnowledgeRequest {
        private String title;
        private String content;
        private String category;
        private String sourceType;
        private Long sourceId;
    }
}
