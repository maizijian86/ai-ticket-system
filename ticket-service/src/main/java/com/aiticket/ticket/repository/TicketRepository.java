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
import java.util.Collection;
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

    // Count by status
    long countByStatusAndDeletedAtIsNull(TicketStatus status);

    // Count OPEN excluding creator
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'OPEN' AND t.deletedAt IS NULL AND t.creatorId != :creatorId")
    long countOpenExcludingCreator(@Param("creatorId") Long creatorId);

    // Count by handler
    long countByHandlerIdAndStatusAndDeletedAtIsNull(Long handlerId, TicketStatus status);

    // Count by creator —— 「待我验收」。PENDING_APPROVAL 下该动作的人是 creator，
    // 不是 handler，所以发布者侧的数字只能这么数。
    long countByCreatorIdAndStatusAndDeletedAtIsNull(Long creatorId, TicketStatus status);

    // Count by participant —— 发布者或接单人都算，「已完成」双方都能看见自己那单。
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.deletedAt IS NULL AND t.status = :status "
            + "AND (t.creatorId = :participantId OR t.handlerId = :participantId)")
    long countByParticipantIdAndStatus(@Param("participantId") Long participantId,
                                       @Param("status") TicketStatus status);

    // ---------------- 大厅分身份卡片的四个数 ----------------

    // 我发布的，不限状态 —— 「我发布的」卡。（和 /my 那个接口同口径，此处只要数字）
    long countByCreatorIdAndDeletedAtIsNull(Long creatorId);

    // 我接的单，不限状态 —— 「我接的单」卡。
    long countByHandlerIdAndDeletedAtIsNull(Long handlerId);

    // 「进行中」卡：归属 × 一组状态。发布者按 creatorId 数，接单者按 handlerId 数。
    // 状态集由 TicketService.ACTIVE_STATUSES 定义，别在这里再写一份字面量。
    long countByCreatorIdAndStatusInAndDeletedAtIsNull(Long creatorId, Collection<TicketStatus> statuses);

    long countByHandlerIdAndStatusInAndDeletedAtIsNull(Long handlerId, Collection<TicketStatus> statuses);

    // 此处原有 5 个 @Modifying 批量 UPDATE（assignHandler / startProcessing /
    // resolve / close / reopen）。它们**从未被调用过** —— TicketService 里所有
    // 状态变更走的都是 findById + 改实体 + save()。已删掉前三个：
    // assignHandler 那条正是"接单有三条路径"里的第三条（零校验、可重复点），
    // 接单现已收敛到 TicketService.pickupTicket 一处。
    //
    // close / reopen 留在下面没动：它们同样是没人调用的死代码，但不属于这次
    // 要收敛的接单路径，留给下次清理，免得这个提交里混进退无用代码的动作。
    //
    // 这些批量 UPDATE 之所以不该复活：它们绕过实体回调与审计字段，状态字段的
    // 唯一写入点应该只有 TicketService。

    // Close ticket
    @Modifying
    @Query("UPDATE Ticket t SET t.status = 'CLOSED', t.closedAt = :now, t.updatedAt = :now WHERE t.id = :ticketId")
    int close(@Param("ticketId") Long ticketId, @Param("now") LocalDateTime now);

    // Reopen ticket
    @Modifying
    @Query("UPDATE Ticket t SET t.status = 'OPEN', t.resolvedAt = NULL, t.closedAt = NULL, t.updatedAt = :now WHERE t.id = :ticketId")
    int reopen(@Param("ticketId") Long ticketId, @Param("now") LocalDateTime now);

    // Find recently resolved for knowledge base
    @Query("SELECT t FROM Ticket t WHERE t.status = 'COMPLETED' AND t.deletedAt IS NULL ORDER BY t.resolvedAt DESC")
    List<Ticket> findRecentlyResolved(Pageable pageable);
}
