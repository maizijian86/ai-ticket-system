package com.aiticket.ai.dto;

import lombok.Data;

@Data
public class RecommendPriceRequest {
    private String content;
    private String category;
    private String priority;
    private String urgency;
}
