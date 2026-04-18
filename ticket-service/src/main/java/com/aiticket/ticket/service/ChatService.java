package com.aiticket.ticket.service;

import com.aiticket.common.exception.BusinessException;
import com.aiticket.ticket.dto.ChatMessageDTO;
import com.aiticket.ticket.entity.Ticket;
import com.aiticket.ticket.entity.TicketChat;
import com.aiticket.ticket.repository.TicketChatRepository;
import com.aiticket.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final TicketChatRepository ticketChatRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public ChatMessageDTO sendMessage(Long ticketId, Long senderId, String senderName, String senderRole, String content) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "Ticket not found"));

        TicketChat chat = new TicketChat();
        chat.setTicketId(ticketId);
        chat.setSenderId(senderId);
        chat.setSenderName(senderName);
        chat.setSenderRole(senderRole);
        chat.setContent(content);

        chat = ticketChatRepository.save(chat);
        log.info("Chat message sent: ticketId={}, senderId={}", ticketId, senderId);
        return toDTO(chat);
    }

    public List<ChatMessageDTO> getChatHistory(Long ticketId) {
        return ticketChatRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteByTicketId(Long ticketId) {
        ticketChatRepository.deleteByTicketId(ticketId);
        log.info("Chat history deleted: ticketId={}", ticketId);
    }

    private ChatMessageDTO toDTO(TicketChat chat) {
        return ChatMessageDTO.builder()
                .id(chat.getId())
                .ticketId(chat.getTicketId())
                .senderId(chat.getSenderId())
                .senderName(chat.getSenderName())
                .senderRole(chat.getSenderRole())
                .content(chat.getContent())
                .createdAt(chat.getCreatedAt())
                .build();
    }
}
