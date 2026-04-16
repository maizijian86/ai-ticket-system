package com.aiticket.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendHandlerResponse {
    private Long handlerId;
    private String handlerName;
    private String reason;
    private Double matchScore;
}
