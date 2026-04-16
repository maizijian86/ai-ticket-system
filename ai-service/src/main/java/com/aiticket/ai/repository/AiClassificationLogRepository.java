package com.aiticket.ai.repository;

import com.aiticket.ai.entity.AiClassificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiClassificationLogRepository extends JpaRepository<AiClassificationLog, Long> {

    List<AiClassificationLog> findByTicketId(Long ticketId);
}
