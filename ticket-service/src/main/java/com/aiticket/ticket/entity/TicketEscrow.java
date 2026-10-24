package com.aiticket.ticket.entity;

import com.aiticket.ticket.enums.EscrowStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单资金托管 —— 一张单在某一轮里押住了发布者多少钱。
 *
 * <p><b>这是「托管金额」的唯一真相。</b>结算与退款一律读 {@link #amount}，
 * **绝不读 {@code ticket.budget}**：发布者在未接单时可以随意改预算，
 * 改完之后 {@code ticket.budget} 与已冻结的金额就不是一回事了。
 * 为了从签名上杜绝误用，{@code WalletService.settleTicket} / {@code refundTicketEscrow}
 * 都**不接收金额参数**，只能进方法内部来读这一行。
 *
 * <p>{@link #round} 是第几次托管：工单关闭后「重新开放」会新起一轮（{@code round+1}），
 * 旧行保持 {@code REFUNDED} 不变 —— 不覆盖历史，才对得上账。
 * 唯一键 {@code (ticket_id, round)} 保证同一轮只有一行。
 */
@Data
@Entity
@Table(
        name = "ticket_escrow",
        uniqueConstraints = @UniqueConstraint(name = "uk_escrow_ticket_round", columnNames = {"ticket_id", "round"}),
        indexes = @Index(name = "idx_escrow_publisher_status", columnList = "publisher_id, status")
)
public class TicketEscrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 第几次托管，从 1 开始。重开会 +1 */
    @Column(name = "round", nullable = false)
    private Integer round = 1;

    /** 谁押的钱（= 工单发布者） */
    @Column(name = "publisher_id", nullable = false)
    private Long publisherId;

    /** 当前托管额。随「未接单时改预算」而变，是 settle/refund 的唯一依据 */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EscrowStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
