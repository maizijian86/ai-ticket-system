package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.MembershipOrder;
import com.aiticket.ticket.enums.MembershipOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MembershipOrderRepository extends JpaRepository<MembershipOrder, Long> {

    Optional<MembershipOrder> findByOutTradeNoAndDeletedAtIsNull(String outTradeNo);

    /**
     * 按商户订单号**加悲观写锁**读订单 —— 到账逻辑（{@code MembershipService.fulfill}）
     * 的第一步，也是整套幂等的真正互斥闸。
     *
     * <p>为什么必须是行锁而不是"读出来看看状态"：支付回调与结果页的主动查单**完全可能
     * 并发**，两边都读到 PENDING，于是两边都去插会员行、发次数。锁住这一行之后，
     * 后到的那个会等锁释放、再看到 PAID 并退出。
     *
     * <p>{@code ai_credit_batch.uk_batch_biz_key}（{@code MEMBERSHIP:{orderId}}）是
     * 最后一道防线，但它**管不住 user_membership**（那张表没有唯一键），
     * 所以不能只靠它。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM MembershipOrder o WHERE o.outTradeNo = :outTradeNo AND o.deletedAt IS NULL")
    Optional<MembershipOrder> findByOutTradeNoForUpdate(@Param("outTradeNo") String outTradeNo);

    /**
     * 「我的订单」列表。**刻意不过滤状态**：已关闭的单也要显示 —— 用户点了开通又关掉
     * 支付页，列表里如实写着「已关闭」比"什么都没发生过"更让人安心，
     * 真出现"我说我付了"的争议时也有单据可查。
     *
     * <p>查询条件 {@code user_id} 由调用方强制取当前登录用户，**不接受前端传 userId** ——
     * 那等于让前端决定能看谁的订单。
     */
    Page<MembershipOrder> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    /**
     * 最近一条某状态的订单，供"连点两下立即开通"的复用闸使用。
     *
     * <p>把时间窗（10 分钟）留在服务层判而不是写进查询：窗口的取值理由（短于支付宝那边
     * 的交易超时，所以复用时那笔交易一定还活着）属于业务约束，写进方法名里会看不出所以然。
     */
    Optional<MembershipOrder> findFirstByUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long userId, MembershipOrderStatus status);
}
