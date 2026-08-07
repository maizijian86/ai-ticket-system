package com.aiticket.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEstimationResponse {
    private Double estimatedHours;
    private String difficulty;
    private String suggestion;
}
