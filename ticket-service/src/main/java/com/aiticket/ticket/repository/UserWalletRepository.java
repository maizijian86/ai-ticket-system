package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.UserWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {

    /**
     * 取钱包行并加悲观写锁（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>所有**改动余额**的路径都必须走这里，包括：
     * 冻结（发布工单）、调额（改预算）、退回（关闭/删除）、结算（验收）、充值到账。
     * 没有这把锁，两个并发请求会各自读到同一个旧余额，然后各自扣款 ——
     * 用户只有 100 元却发出两张 100 元的单。
     *
     * <p>注意：只对**已存在**的行加锁。行不存在时会返回 empty，
     * 由 {@code WalletService.walletOrCreate} 先插入再重新加锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM UserWallet w WHERE w.userId = :userId")
    Optional<UserWallet> findByUserIdForUpdate(@Param("userId") Long userId);
}
