package com.aiticket.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskEstimationRequest {
    @NotBlank(message = "任务描述不能为空")
    private String description;

    private String techStack;
}
