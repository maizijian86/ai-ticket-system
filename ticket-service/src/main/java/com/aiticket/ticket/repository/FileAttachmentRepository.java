package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    /**
     * 一个工单的交付物，**新的在前** —— 被驳回后重新交付会留下第二行，
     * 验收页要一眼看到最新那次交的是什么。
     */
    List<FileAttachment> findByTicketIdOrderByIdDesc(Long ticketId);
}
