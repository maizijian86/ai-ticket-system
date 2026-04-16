package com.aiticket.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSkillDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String[] skillTags;
    private Map<String, Integer> expertiseLevel;
    private Integer totalResolved;
    private BigDecimal avgResolutionHours;
    private BigDecimal satisfactionScore;
    private Integer currentLoad;
    private Integer maxLoad;

    // Computed field for recommendation
    private Double recommendationScore;
    private String recommendationReason;
}
