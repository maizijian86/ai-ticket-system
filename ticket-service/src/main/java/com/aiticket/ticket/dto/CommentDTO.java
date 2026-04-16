package com.aiticket.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {

    private Long id;
    private Long ticketId;
    private Long userId;
    private String userName;
    private String content;
    private Boolean isInternal;
    private Boolean isAiSuggested;
    private List<Map<String, Object>> references;
    private LocalDateTime createdAt;
}
