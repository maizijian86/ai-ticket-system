package com.aiticket.ticket.repository;

import com.aiticket.common.enums.Priority;
import com.aiticket.common.enums.TicketCategory;
import com.aiticket.common.enums.TicketStatus;
import com.aiticket.ticket.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    // Find by creator
    Page<Ticket> findByCreatorIdAndDeletedAtIsNull(Long creatorId, Pageable pageable);

    // Find by handler
    Page<Ticket> findByHandlerIdAndDeletedAtIsNull(Long handlerId, Pageable pageable);

    // Find by status
    Page<Ticket> findByStatusAndDeletedAtIsNull(TicketStatus status, Pageable pageable);

    // Find by category
    Page<Ticket> findByCategoryAndDeletedAtIsNull(TicketCategory category, Pageable pageable);

    // Find by priority
    Page<Ticket> findByPriorityAndDeletedAtIsNull(Priority priority, Pageable pageable);

    // Open tickets (unassigned)
    @Query("SELECT t FROM Ticket t WHERE t.handlerId IS NULL AND t.deletedAt IS NULL AND t.status = 'OPEN'")
    Page<Ticket> findOpenTickets(Pageable pageable);

    // Count by status
    long countByStatusAndDeletedAtIsNull(TicketStatus status);

    // Count by handler
    long countByHandlerIdAndStatusAndDeletedAtIsNull(Long handlerId, TicketStatus status);

    // Update handler
    @Modifying
    @Query("UPDATE Ticket t SET t.handlerId = :handlerId, t.handlerName = :handlerName, t.updatedAt = :now WHERE t.id = :ticketId")
    int assignHandler(@Param("ticketId") Long ticketId,
                      @Param("handlerId") Long handlerId,
                      @Param("handlerName") String handlerName,
                      @Param("now") LocalDateTime now);

    // Update status to processing
    @Modifying
    @Query("UPDATE Ticket t SET t.status = 'PROCESSING', t.updatedAt = :now WHERE t.id = :ticketId AND t.status = 'OPEN'")
    int startProcessing(@Param("ticketId") Long ticketId, @Param("now") LocalDateTime now);

    // Resolve ticket
    @Modifying
    @Query("UPDATE Ticket t SET t.status = 'RESOLVED', t.resolvedAt = :now, t.updatedAt = :now WHERE t.id = :ticketId")
    int resolve(@Param("ticketId") Long ticketId, @Param("now") LocalDateTime now);

    // Close ticket
    @Modifying
    @Query("UPDATE Ticket t SET t.status = 'CLOSED', t.closedAt = :now, t.updatedAt = :now WHERE t.id = :ticketId")
    int close(@Param("ticketId") Long ticketId, @Param("now") LocalDateTime now);

    // Reopen ticket
    @Modifying
    @Query("UPDATE Ticket t SET t.status = 'OPEN', t.resolvedAt = NULL, t.closedAt = NULL, t.updatedAt = :now WHERE t.id = :ticketId")
    int reopen(@Param("ticketId") Long ticketId, @Param("now") LocalDateTime now);

    // Find recently resolved for knowledge base
    @Query("SELECT t FROM Ticket t WHERE t.status = 'RESOLVED' AND t.deletedAt IS NULL ORDER BY t.resolvedAt DESC")
    List<Ticket> findRecentlyResolved(Pageable pageable);
}
