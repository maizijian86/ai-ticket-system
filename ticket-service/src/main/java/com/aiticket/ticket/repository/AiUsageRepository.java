package com.aiticket.ticket.repository;

import com.aiticket.ticket.entity.AiUsage;
import com.aiticket.ticket.enums.AiFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    /**
     * 取"有效占位行"：{@code active_slot = 1}。
     *
     * <p>因为唯一键 {@code (ticket_id, feature, active_slot)} 在 {@code active_slot=1}
     * 上只允许一行，所以这里返回 Optional 是安全的。失败退款的行会被置成
     * {@code active_slot = NULL}，从此不参与唯一约束、也查不到 —— 用户可以重试。
     */
    Optional<AiUsage> findByTicketIdAndFeatureAndActiveSlot(Long ticketId, AiFeature feature, Integer activeSlot);

    /** 该工单全部功能的有效占位行，一次查出来拼前端要的额度状态，省两次往返。 */
    List<AiUsage> findByTicketIdAndActiveSlot(Long ticketId, Integer activeSlot);
}
