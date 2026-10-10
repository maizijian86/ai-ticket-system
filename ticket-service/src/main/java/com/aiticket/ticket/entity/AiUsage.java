package com.aiticket.ticket.entity;

import com.aiticket.common.entity.BaseEntity;
import com.aiticket.ticket.enums.AiFeature;
import com.aiticket.ticket.enums.AiUsageStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 额度使用流水 —— 一行 = 一次功能调用尝试。
 *
 * <p>这张表同时干三件事，所以只有两张表就够（没有单独的"消耗流水表"）：
 * <ol>
 *   <li><b>消耗流水</b>：谁 / 何时 / 哪张工单 / 哪个功能 / 扣了哪个批次 / 结果如何；</li>
 *   <li><b>"每工单每槽位一次"的权威约束</b>：唯一键 {@code (ticket_id, feature, active_slot)}。
 *       成功过的行永久占位（{@code active_slot=1}），同票同槽位再插必然冲突 ——
 *       并发、多实例、Redis 挂了都成立，这就是不依赖分布式锁的原因。
 *       <b>槽位不等于功能</b>：分析与对话共用 {@code feature=ANALYSIS} 这一行，
 *       所以一张工单最多两行成功记录（分析与对话一行、PRD 一行）。</li>
 *   <li><b>AI 对话的解锁凭证</b>：存在一行 {@code feature=ANALYSIS, status=SUCCESS} 就是
 *       "本工单的 AI 对话已解锁"，之后多轮对话不再插行、不再扣费。历史上对话单独记在
 *       {@code feature=CHAT} 上的行也认（合并之前的数据），读取时两者同属一格。</li>
 * </ol>
 *
 * <p>{@code active_slot} 为 NULL 表示"这次尝试已作废、槽位已释放"（AI 调用失败退款后
 * 走这条），用户于是可以重试。之所以用 NULL 而不是 0：MySQL 的唯一索引不约束 NULL，
 * 所以同一张工单可以留下**多行**失败记录（审计价值）而只有一行有效占位。
 */
@Data
@Entity
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "ai_usage",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_usage_slot",
                columnNames = {"ticket_id", "feature", "active_slot"}
        ),
        // 两个都显式声明：@UniqueConstraint 不会顺带生成普通索引，
        // 不写这两行，建出来的表就和 db/schema.sql 里的 DDL 对不上。
        indexes = {
                @Index(name = "idx_usage_user", columnList = "user_id"),
                @Index(name = "idx_usage_batch", columnList = "batch_id")
        }
)
public class AiUsage extends BaseEntity {

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 消耗者，即当时的接单人 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 20)
    private AiFeature feature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiUsageStatus status;

    /** 1 = 占用中；NULL = 已释放（唯一键不约束 NULL，所以失败后可重新占位） */
    @Column(name = "active_slot")
    private Integer activeSlot = 1;

    /** 实际扣减的批次，退款回滚到同一批（避免"从别的批次退回来"把过期时间搞乱） */
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "amount", nullable = false)
    private Integer amount = 1;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
