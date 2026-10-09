package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.AiCreditBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiCreditBatchRepository extends JpaRepository<AiCreditBatch, Long> {

    /**
     * 可用批次：还有剩余、未过期。
     *
     * <p><b>刻意不写 ORDER BY</b>，排序交给 {@code AiQuotaService} 在 Java 里做。
     * 原因在加锁版本 {@link #lockUsable} 上：InnoDB 的 {@code FOR UPDATE} 是按查询
     * 实际读到的顺序加行锁的，而"先花快过期的"这个顺序需要 CASE 表达式
     * （把 {@code expires_at IS NULL} 排到最后），HQL 对 ORDER BY 里 CASE 的支持
     * 依赖具体版本 —— 与其赌它，不如让两条路径都用同一个索引扫描顺序
     * （{@code idx_batch_user_expiry (user_id, expires_at)}），排序只在内存里做。
     * 同一用户的可用批次通常只有 1~3 行，内存排序没有成本。
     */
    @Query("""
            SELECT b FROM AiCreditBatch b
            WHERE b.userId = :userId
              AND b.remainingAmount > 0
              AND b.deletedAt IS NULL
              AND (b.expiresAt IS NULL OR b.expiresAt > :now)
            """)
    List<AiCreditBatch> findUsable(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 同 {@link #findUsable}，但加悲观写锁，供扣减时使用。
     *
     * <p>锁住**该用户全部可用批次**（而不是只锁将要用到的那一行），是刻意的：
     * 同一组行、同一个扫描顺序，两个并发事务的加锁顺序就必然一致，不会死锁。
     * 只锁"第一个"反而会因为各自的第一个不同而互相等待。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM AiCreditBatch b
            WHERE b.userId = :userId
              AND b.remainingAmount > 0
              AND b.deletedAt IS NULL
              AND (b.expiresAt IS NULL OR b.expiresAt > :now)
            """)
    List<AiCreditBatch> lockUsable(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 退款要退回**原来那一批**，同样得加锁，否则和并发的扣减互相覆盖。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM AiCreditBatch b WHERE b.id = :id")
    Optional<AiCreditBatch> findByIdForUpdate(@Param("id") Long id);

    boolean existsByBizKey(String bizKey);

    /**
     * 按幂等键批量取批次。
     *
     * <p>给会员订单用：订单的会员到期日不存订单表，而在发放出去的那个批次上
     * （{@code biz_key = MEMBERSHIP:{orderId}}）。「我的订单」列表一次要显示 10 条订单，
     * 逐条查会变成 N+1，所以按 biz_key 集合一次取回来。
     */
    List<AiCreditBatch> findByBizKeyIn(Collection<String> bizKeys);

    /**
     * 幂等发放：{@code biz_key} 上唯一键 + {@code INSERT IGNORE}，重复发放静默跳过。
     *
     * <p>不用"先查再插"是因为那在并发下挡不住：新用户同时打开两个请求、两处都触发
     * 首次赠送时会各插一行。走 INSERT IGNORE 则由数据库裁决，且不必为此把整个外层
     * 事务标记成 rollback-only（捕获唯一键冲突异常会污染 Hibernate 会话状态）。
     *
     * @return 1 = 真的发了一批；0 = 这个幂等键已经发过，本次什么都没做
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT IGNORE INTO ai_credit_batch
                (user_id, source, biz_key, total_amount, remaining_amount, expires_at, granted_at, remark, created_at, updated_at)
            VALUES
                (:userId, :source, :bizKey, :amount, :amount, :expiresAt, :grantedAt, :remark, NOW(), NOW())
            """)
    int insertIgnore(@Param("userId") Long userId,
                     @Param("source") String source,
                     @Param("bizKey") String bizKey,
                     @Param("amount") int amount,
                     @Param("expiresAt") LocalDateTime expiresAt,
                     @Param("grantedAt") LocalDateTime grantedAt,
                     @Param("remark") String remark);
}
