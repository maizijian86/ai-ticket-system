package com.aiticket.ai.controller;

import com.aiticket.ai.dto.*;
import com.aiticket.ai.entity.KnowledgeBase;
import com.aiticket.ai.service.AiService;
import com.aiticket.ai.service.DeveloperAiService;
import com.aiticket.common.dto.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final DeveloperAiService developerAiService;

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

    /**
     * 生成PRD并下载为Markdown文件
     */
    @PostMapping("/generate-prd")
    public void generatePrd(@Valid @RequestBody PrdGenerateRequest request,
                            HttpServletResponse response) throws IOException {
        // 调用AI生成PRD
        String prdContent = developerAiService.generatePrd(
                request.getUserId(),
                request.getProjectInfo()
        );

        // 设置响应头，触发下载
        response.setContentType("text/markdown; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String fileName = "PRD_" + System.currentTimeMillis() + ".md";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        // 写入文件内容
        PrintWriter out = response.getWriter();
        out.print(prdContent);
        out.flush();
    }

    @lombok.Data
    public static class PrdGenerateRequest {
        private Long userId;
        private String projectInfo;
    }

    // ==================== Project Analysis APIs ====================

    @PostMapping("/analyze-project")
    public Result<ProjectAnalysisResponse> analyzeProject(@Valid @RequestBody ProjectAnalysisRequest request) {
        AiService.ProjectAnalysisResult result = aiService.analyzeProject(request.getDescription());
        return Result.success(ProjectAnalysisResponse.builder()
                .projectName(result.projectName())
                .overview(result.overview())
                .targetUsers(result.targetUsers())
                .rawJson(result.rawJson())
                .estimatedDays(result.estimatedDays())
                .build());
    }

    @PostMapping("/decompose-tasks")
    public Result<TaskDecompositionResponse> decomposeTasks(@Valid @RequestBody TaskDecompositionRequest request) {
        AiService.TaskDecompositionResult result = aiService.decomposeTasks(request.getDescription(), request.getPrd());
        return Result.success(TaskDecompositionResponse.builder()
                .rawJson(result.rawJson())
                .build());
    }

    @PostMapping("/estimate-task")
    public Result<TaskEstimationResponse> estimateTask(@Valid @RequestBody TaskEstimationRequest request) {
        AiService.TaskEstimationResult result = aiService.estimateTask(request.getDescription(), request.getTechStack());
        return Result.success(TaskEstimationResponse.builder()
                .estimatedHours(result.estimatedHours())
                .difficulty(result.difficulty())
                .suggestion(result.rawJson())
                .build());
    }

    @PostMapping("/recommend-tech-stack")
    public Result<TechStackResponse> recommendTechStack(@Valid @RequestBody TechStackRequest request) {
        AiService.TechStackResult result = aiService.recommendTechStack(request.getDescription());
        return Result.success(TechStackResponse.builder()
                .frontend(result.frontend())
                .backend(result.backend())
                .database(result.database())
                .reason(result.rawJson())
                .build());
    }

    @PostMapping("/generate-milestones")
    public Result<MilestoneResponse> generateMilestones(@Valid @RequestBody MilestoneRequest request) {
        int days = request.getEstimatedDays() != null ? request.getEstimatedDays() : 14;
        List<AiService.MilestoneSuggestion> suggestions = aiService.generateMilestones(request.getDescription(), days);
        List<MilestoneResponse.MilestoneItem> items = suggestions.stream()
                .map(s -> MilestoneResponse.MilestoneItem.builder()
                        .name(s.name())
                        .description(s.description())
                        .dayOffset(s.dayOffset())
                        .build())
                .toList();
        return Result.success(MilestoneResponse.builder().milestones(items).build());
    }
}
