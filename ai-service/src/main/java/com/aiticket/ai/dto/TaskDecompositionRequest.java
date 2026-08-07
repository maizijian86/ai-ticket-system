package com.aiticket.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskDecompositionRequest {
    @NotBlank(message = "项目描述不能为空")
    private String description;

    private String prd;
}
