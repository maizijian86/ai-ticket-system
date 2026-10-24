package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.WalletOrder;
import com.aiticket.ticket.enums.WalletOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface WalletOrderRepository extends JpaRepository<WalletOrder, Long> {

    Optional<WalletOrder> findByOutTradeNoAndDeletedAtIsNull(String outTradeNo);

    /**
     * 到账/关单时按单号取行**并加写锁**。
     *
     * <p>这是「重复回调只加一次钱」的第一道闸：两个回调同时到达时，第二个会在这里等待，
     * 等第一个提交后再读，就能看到 {@code status = PAID} 从而跳过。
     * 第二道闸是 {@code wallet_transaction} 的唯一键（见 {@code WalletTransaction}）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM WalletOrder o WHERE o.outTradeNo = :outTradeNo AND o.deletedAt IS NULL")
    Optional<WalletOrder> findByOutTradeNoForUpdate(@Param("outTradeNo") String outTradeNo);

    /** 防连点：同额度的未支付单在窗口内直接复用（见 WalletService.reusablePendingOrder） */
    Optional<WalletOrder> findFirstByUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long userId, WalletOrderStatus status);

    /** 「我的充值记录」列表 */
    org.springframework.data.domain.Page<WalletOrder> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long userId, org.springframework.data.domain.Pageable pageable);

    /** 对账用：这段时间里已到账的总额（调试/核对时手工跑） */
    @Query("""
            SELECT COALESCE(SUM(o.amount), 0) FROM WalletOrder o
            WHERE o.userId = :userId AND o.status = com.aiticket.ticket.enums.WalletOrderStatus.PAID
              AND o.paidAt >= :since AND o.deletedAt IS NULL
            """)
    java.math.BigDecimal sumPaidSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
