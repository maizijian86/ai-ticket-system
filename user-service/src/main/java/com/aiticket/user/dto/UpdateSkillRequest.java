package com.aiticket.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateSkillRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    private String[] skillTags;

    private Map<String, Integer> expertiseLevel;

    private Integer maxLoad;
}
