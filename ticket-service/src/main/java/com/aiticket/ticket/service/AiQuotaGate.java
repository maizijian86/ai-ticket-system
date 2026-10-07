package com.aiticket.ticket.service;

import com.aiticket.ticket.entity.AiUsage;
import com.aiticket.ticket.enums.AiFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 把"预占额度 → 调 AI → 结算"串起来的编排层。
 *
 * <p><b>本类刻意不加 {@code @Transactional}</b>。骨架必须是三个独立的事务段：
 *
 * <pre>
 *   短事务A: 预占（插占位行 + 扣次数）并提交
 *   ──────── 事务外：调用 AI，可能几分钟 ────────
 *   短事务B: 成功落定 / 失败退款
 * </pre>
 *
 * <p>为什么不能图省事把整段圈进一个事务：模型最慢能跑满 10 分钟，而 Hikari 连接池
 * 默认只有 10 条。事务跨 AI 意味着每来一个请求就占住一条连接好几分钟，十几个并发
 * 请求就能把整个 ticket-service 的数据库连接抽干、连普通列表接口都查不动。
 *
 * <p>事务是由 {@link AiQuotaService} 上的 {@code @Transactional} 提供的 —— 它是独立
 * bean，Spring 代理生效。本类只负责"什么时候调用"，不负责"事务怎么划"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuotaGate {

    private final AiQuotaService quotaService;

    /**
     * 消耗 1 次额度执行一次 AI 调用。
     *
     * <p>失败语义：{@code aiCall} 抛出任何 {@link RuntimeException} 都会**退回额度**
     * 并把占位行释放（{@code active_slot = NULL}），用户可以立刻重试。只有 AI 调用本身
     * 失败才退 —— 工单此后被驳回、关闭、删除、接单人登出，一律不退，否则"用完把工单
     * 关掉就能拿回次数"就成了刷额度漏洞。
     *
     * <p>只捕 {@code RuntimeException} 而不捕 {@code Error}：{@code Error} 意味着 JVM
     * 已经处于不可信状态，此时还能不能写库都成问题，硬要兜反而会掩盖真问题。这条路
     * 留下的 RESERVED 残留行由 {@link AiQuotaService#reclaimIfStale} 在 30 分钟后接管退款。
     *
     * @param aiCall 真正的 AI 调用。可能耗时数分钟，**不能**在里面碰事务资源
     * @throws com.aiticket.common.exception.BusinessException 409 已用过/处理中；402 额度不足
     */
    public <T> T run(Long ticketId, Long userId, AiFeature feature, Supplier<T> aiCall) {
        AiUsage usage = quotaService.reserve(ticketId, userId, feature); // 短事务A，在这里提交
        try {
            T result = aiCall.get();                    // 无事务，可能几分钟
            quotaService.markSuccess(usage.getId());    // 短事务B
            return result;
        } catch (RuntimeException e) {
            log.warn("AI 调用失败，退回额度: usageId={}, ticketId={}, feature={}, reason={}",
                    usage.getId(), ticketId, feature, e.getMessage());
            quotaService.refund(usage.getId(), e.getMessage()); // 短事务B'，失败也要退
            throw e;
        }
    }
}
