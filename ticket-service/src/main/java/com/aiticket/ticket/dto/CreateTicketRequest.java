package com.aiticket.ticket.dto;

import com.aiticket.common.enums.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CreateTicketRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must be less than 500 characters")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    private Urgency urgency = Urgency.NORMAL;

    private List<Map<String, String>> attachments; // [{name, path, size, type}]

    private List<Map<String, String>> githubRepos; // [{name, url}]

    private BigDecimal price; // User confirmed price (AI suggested + user modified)

    private BigDecimal aiPriceSuggestion; // AI suggested price for reference

    // ==================== 开发者需求字段 ====================

    // 项目类型（JavaWeb/Python/前端/全栈/移动端）
    private String projectType;

    // 技术栈（如：Spring Boot, Vue, MyBatis等）
    private String techStack;

    // 预算
    private BigDecimal budget;

    // 截止日期
    private LocalDateTime deadline;

    // 详细需求描述
    private String detailedRequirements;

    // ==================== AI 额度 ====================

    /**
     * 发布时是否消耗自己 1 次 AI 额度附在本工单上。
     *
     * 附上后：接单人用「AI 分析与对话」不扣他自己的次数（PRD 不在内，仍由接单人自付）；
     * 该单在大厅置顶并标注。**发布即消耗、不退还**（没人接、关闭、删除都不退）。
     * 为空按 false 处理。额度不足时整个发布失败（402），工单不会创建。
     */
    private Boolean useAiCredit;
}
