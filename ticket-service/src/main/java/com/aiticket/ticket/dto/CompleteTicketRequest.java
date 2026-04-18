package com.aiticket.ticket.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CompleteTicketRequest {
    // Completion proof - GitHub link or description
    private String completionProof;

    // Updated GitHub repos (optional - if handler updated them)
    private List<Map<String, String>> githubRepos;
}
