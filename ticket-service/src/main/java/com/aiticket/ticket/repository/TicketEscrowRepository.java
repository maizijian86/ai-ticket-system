package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.TicketEscrow;
import com.aiticket.ticket.enums.EscrowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface TicketEscrowRepository extends JpaRepository<TicketEscrow, Long> {

    /**
     * 取该工单当前仍在托管中的那一行并加写锁。
     *
     * <p>锁它的理由：改预算要"读当前额 → 算差额 → 写新额"，两个并发请求都读到旧额
     * 就会各加各的，钱算错。结算/退回同样要锁，避免同一笔被处理两次。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM TicketEscrow e
            WHERE e.ticketId = :ticketId AND e.status = com.aiticket.ticket.enums.EscrowStatus.FROZEN
            """)
    Optional<TicketEscrow> findFrozenForUpdate(@Param("ticketId") Long ticketId);

    /** 读（不加锁）：给 DTO 判断工单是否托管过，以及前端 funded 标记用 */
    Optional<TicketEscrow> findFirstByTicketIdAndStatusOrderByRoundDesc(Long ticketId, EscrowStatus status);

    /** 该工单托管过几轮 —— 重开时用来算下一轮的 round */
    @Query("SELECT COALESCE(MAX(e.round), 0) FROM TicketEscrow e WHERE e.ticketId = :ticketId")
    int maxRound(@Param("ticketId") Long ticketId);

    /** 该用户仍在托管中的总额，供对账用（应等于 user_wallet.frozen_amount） */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM TicketEscrow e
            WHERE e.publisherId = :publisherId AND e.status = com.aiticket.ticket.enums.EscrowStatus.FROZEN
            """)
    BigDecimal sumFrozenByPublisher(@Param("publisherId") Long publisherId);
    /**
     * 该用户**实际付出去的工单款**合计（个人统计用）。
     *
     * <p>只数 {@code SETTLED} 的行：一张单只有一行，金额就是当初结算的金额。
     * 用流水的 {@code SETTLE_OUT} 也能算（{@code SUM(-frozen_delta)}），
     * 但那要把符号掰一次，容易写反。
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM TicketEscrow e
            WHERE e.publisherId = :publisherId AND e.status = com.aiticket.ticket.enums.EscrowStatus.SETTLED
            """)
    BigDecimal sumSettledByPublisher(@Param("publisherId") Long publisherId);
}