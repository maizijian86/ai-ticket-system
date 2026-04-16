package com.aiticket.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriorityResponse {
    private String priority;  // P0, P1, P2, P3
    private BigDecimal score;  // 0.0 - 100.0
    private String factors;  // JSON string of factor analysis
}
