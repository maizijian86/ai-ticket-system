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

    /**
     * {@code ChatMessageDTO} 是私聊和 AI 对话**共用**的，所以它身上有两套表述同一件事的
     * 字段：私聊这条路径填 {@code senderId}/{@code senderName}/{@code senderRole}，
     * AI 对话那条在控制器里自己 builder 出 {@code userId}/{@code userName}/
     * {@code senderType="AI"}（见 TicketController.aiChat）。
     *
     * <p>而前端 TicketChat.vue 读的恰恰是后一套（`msg.userId === currentUserId` 判断
     * 哪条是自己的、`msg.userName` 显示发送者），于是**发送者名字一直是空的、
     * "我"的气泡也永远标不出来**。这里把前一套映射过去补齐，不删任何字段 ——
     * AI 那条路径不经过这个方法，动结构只会平白牵动它。
     */
    private ChatMessageDTO toDTO(TicketChat chat) {
        return ChatMessageDTO.builder()
                .id(chat.getId())
                .ticketId(chat.getTicketId())
                .senderId(chat.getSenderId())
                .userId(chat.getSenderId())
                .senderName(chat.getSenderName())
                .userName(chat.getSenderName())
                .senderRole(chat.getSenderRole())
                // 私聊里没有 AI 发言，固定 USER；前端的 AI 分支在这条流上是死代码
                .senderType("USER")
                .content(chat.getContent())
                .createdAt(chat.getCreatedAt())
                .build();
    }
}
