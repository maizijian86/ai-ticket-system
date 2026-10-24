package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.WalletTransaction;
import com.aiticket.ticket.enums.WalletTxType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Page<WalletTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /**
     * 该工单某一轮「累计押了多少钱」= 冻结 + 所有调额的头寸。
     *
     * <p>用来和 {@code ticket_escrow.amount} 交叉验证：两者不一致说明有代码路径漏记流水，
     * 此时结算要报错而不是照着一方动钱。注意只取 {@code FREEZE} 与 {@code ESCROW_ADJUST}
     * 两类，**不含** REFUND / SETTLE_*（那些是释放动作）。
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t
            WHERE t.refType = 'TICKET' AND t.refId = :ticketId AND t.escrowRound = :round
              AND t.type IN (com.aiticket.ticket.enums.WalletTxType.FREEZE,
                             com.aiticket.ticket.enums.WalletTxType.ESCROW_ADJUST)
            """)
    BigDecimal sumLockedAmount(@Param("ticketId") Long ticketId, @Param("round") Integer round);

    /** 对账：该用户可用余额的累积变动，应等于 {@code user_wallet.available_amount} */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t WHERE t.userId = :userId")
    BigDecimal sumAmountByUser(@Param("userId") Long userId);

    /** 对账：该用户冻结额的累积变动，应等于 {@code user_wallet.frozen_amount} */
    @Query("SELECT COALESCE(SUM(t.frozenDelta), 0) FROM WalletTransaction t WHERE t.userId = :userId")
    BigDecimal sumFrozenDeltaByUser(@Param("userId") Long userId);

    /** 该工单是否已有某类流水（幂等预检，非权威；权威是唯一键） */
    boolean existsByRefTypeAndRefIdAndEscrowRoundAndType(
            String refType, Long refId, Integer escrowRound, WalletTxType type);

    List<WalletTransaction> findByRefTypeAndRefIdOrderByIdAsc(String refType, Long refId);
    /**
     * 该用户**实际收到的工单款**合计（个人统计用）。
     *
     * <p>只数 {@code SETTLE_IN}：那是"钱进我可用余额"的唯一动作。
     * 不用 {@code SUM(budget)} —— 预算可能被人改过，而且历史工单（钱包上线前创建）
     * 没有托管、从未结算，用预算会把它们算成收入。
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t
            WHERE t.userId = :userId AND t.type = com.aiticket.ticket.enums.WalletTxType.SETTLE_IN
            """)
    BigDecimal sumSettledIn(@Param("userId") Long userId);
}