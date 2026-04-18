package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.TicketChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketChatRepository extends JpaRepository<TicketChat, Long> {
    List<TicketChat> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    void deleteByTicketId(Long ticketId);
}
