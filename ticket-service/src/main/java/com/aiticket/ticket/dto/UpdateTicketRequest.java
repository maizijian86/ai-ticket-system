package com.aiticket.ticket.dto;

import com.aiticket.common.enums.Priority;
import com.aiticket.common.enums.TicketCategory;
import com.aiticket.common.enums.Urgency;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class UpdateTicketRequest {

    @Size(max = 500, message = "Title must be less than 500 characters")
    private String title;

    private String content;

    private TicketCategory category;

    private Priority priority;

    private Urgency urgency;

    private List<Map<String, String>> attachments;

    private List<Map<String, String>> githubRepos;

    private BigDecimal price;

    // ==================== 开发者需求字段 ====================
    //
    // 这几项在 CreateTicketRequest 里是发布时一次填好的，但更新接口此前只认
    // title/content/category/priority/urgency —— 于是"预算填错了"在界面上没有任何
    // 补救办法：发布者只能删了重发，连带丢掉已经预附在那张单上的 AI 额度。
    //
    // 字段名与 CreateTicketRequest **逐字一致**，前端因此可以把同一个表单组件
    // 复用到编辑页（见 frontend/src/components/ticket/TicketForm.tsx）。
    //
    // 全部允许为 null：null 表示"这次不改这一项"，与上面几个字段的语义完全一致。

    private String projectType;

    private String techStack;

    private BigDecimal budget;

    private LocalDateTime deadline;

    private String detailedRequirements;
}
