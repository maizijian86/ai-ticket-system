package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.UserMembership;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserMembershipRepository extends JpaRepository<UserMembership, Long> {

    /**
     * 该用户当前有效的会员周期（{@code status=ACTIVE} 且未过期）。
     *
     * <p><b>「是不是会员」一律用这个条件实时判定，不读任何缓存字段</b>：
     * 若在别处维护一个 {@code is_member} 布尔或依赖"到期时记得改成 EXPIRED"，
     * 到期当天就会出现"缓存说还是会员、次数却已经不可用"的矛盾状态。
     * {@code status} 里的 {@code EXPIRED} 只是运营标记，不参与业务判断。
     */
    @Query("""
            SELECT m FROM UserMembership m
            WHERE m.userId = :userId
              AND m.status = 'ACTIVE'
              AND m.expiresAt > :now
              AND m.deletedAt IS NULL
            """)
    List<UserMembership> findActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 该用户**全部**会员行（不分状态）加悲观写锁，供开通/续费时计算新周期的起点。
     *
     * <p>锁的是"这个用户的所有会员行"而不是"将要用到的那一行"，理由与
     * {@code AiCreditBatchRepository.lockUsable} 一致，但这里还多一层：
     * <b>没有现存会员行的用户也必须被挡住</b>。锁住 {@code user_id = ?} 这个范围，
     * InnoDB 在 REPEATABLE READ 下会连**间隙**一起锁上，于是两个并发的"首次开通"
     * 不会各自算出同一个起点、插出两段完全重叠的会员期（用户付了两次钱只拿到 30 天）。
     *
     * <p>⚠️ 前提是隔离级别为 MySQL 默认的 REPEATABLE READ。若改成 READ COMMITTED，
     * 间隙锁失效，这条保护会退化成"只挡住已有会员行的用户"—— 那时要靠
     * {@code ai_credit_batch} 的唯一键保次数，而 {@code user_membership} 没有唯一键，
     * 可能出现两条重叠的会员行（次数不会多发）。部署时不要动隔离级别。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM UserMembership m WHERE m.userId = :userId AND m.deletedAt IS NULL")
    List<UserMembership> lockByUserId(@Param("userId") Long userId);
}
