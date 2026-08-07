package com.aiticket.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechStackResponse {
    private String frontend;
    private String backend;
    private String database;
    private String others;
    private String reason;
}
