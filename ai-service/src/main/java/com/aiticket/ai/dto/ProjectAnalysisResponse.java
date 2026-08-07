package com.aiticket.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAnalysisResponse {
    private String projectName;
    private String overview;
    private String targetUsers;
    private String rawJson;
    private Integer estimatedDays;
}
