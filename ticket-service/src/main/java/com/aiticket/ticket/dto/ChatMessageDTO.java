package com.aiticket.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private Long id;
    private Long ticketId;
    private Long senderId;
    private Long userId;
    private String senderName;
    private String userName;
    private String senderRole;
    private String senderType;
    private String content;
    private LocalDateTime createdAt;
}
