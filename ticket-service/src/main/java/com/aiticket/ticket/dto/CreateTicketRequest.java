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
}
