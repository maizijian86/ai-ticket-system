package com.aiticket.ticket.service;

import com.aiticket.common.exception.BusinessException;
import com.aiticket.ticket.dto.AiQuotaStatusDTO;
import com.aiticket.ticket.entity.AiCreditBatch;
import com.aiticket.ticket.entity.AiUsage;
import com.aiticket.ticket.enums.AiFeature;
import com.aiticket.ticket.enums.AiFeatureState;
import com.aiticket.ticket.enums.AiUsageStatus;
import com.aiticket.ticket.enums.CreditSource;
import com.aiticket.ticket.repository.AiCreditBatchRepository;
import com.aiticket.ticket.repository.AiUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 辅助开发额度的记账与门禁。
 *
 * <p>三件事：发放（批次）、扣减（预占）、结算（成功/退款）。所有方法都是**短事务** ——
 * 调用 AI 的那几分钟绝不能落在事务里，那会长时间占住数据库连接（Hikari 池默认只有
 * 10 条），几笔并发就把整个服务拖垮。谁负责把"预占 → 调 AI → 结算"串起来，见
 * {@link AiQuotaGate}。
 *
 * <p>这些方法必须是**独立 bean 上的 public 方法**：如果把它们挪进调用方类里当成私有
 * 方法调用，Spring 的事务代理不生效，{@code @Transactional} 形同虚设、预占就没有原子性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuotaService {

    /**
     * 新用户注册赠送次数。
     *
     * <p><b>改这里要同时改前端 {@code frontend/src/utils/ai-quota.ts} 的
     * {@code SIGNUP_BONUS_CREDITS}</b> —— 大厅横幅和登录卡片上的"送 5 次"是写死的
     * 文案，两边不同步就会出现"说送 5 次、实际到账 3 次"。
     */
    public static final int SIGNUP_BONUS_CREDITS = 5;

    /** 有效占位行的取值。退款时置 NULL 以释放槽位（唯一键不约束 NULL） */
    private static final int ACTIVE_SLOT = 1;

    /**
     * 预占行超过这个时长还没结算，就认为当初那个进程已经死了，自动退款。
     *
     * <p>阈值取 30 分钟而不是贴着超时时间：单次模型调用允许 5 分钟、失败还会重试，
     * ai-analysis 内部串行调两次，最坏情况逼近 20 分钟。阈值一旦小于真实耗时，
     * 接管就会和仍在跑的请求赛跑 —— 那个请求随后写下 SUCCESS 时额度已经退回去了。
     * {@link #markSuccess} 对"行已不是 RESERVED"的情况做了兜底，不会把账改乱，
     * 但那种情况应该尽可能不发生。
     */
    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private static final int FAILURE_REASON_MAX = 500;

    private final AiCreditBatchRepository batchRepository;
    private final AiUsageRepository usageRepository;

    /** 账户可用额度与最近的过期时间 */
    public record Balance(int remaining, LocalDateTime nextExpireAt) {}

    // ==================== 发放 ====================

    /**
     * 补发新用户注册赠送（幂等）。
     *
     * <p>刻意**不挂在注册流程里**：注册在 user-service，而额度表属于 ticket-service
     * （只有它是 ddl-auto: update），跨服务发放要引 Feign + 分布式事务，为了送 5 次
     * 额度不值得。改成"首次访问额度时补发"，效果一样，且幂等键保证了重复触发只发一次。
     */
    @Transactional
    public void ensureRegisterGrant(Long userId) {
        if (userId == null) {
            return;
        }
        // 自己调自己：@Transactional 不会另开事务，而是并入调用方那个 —— 这正是想要的，
        // 补发和随后的读取/扣减在同一个事务里，看不到"发了但还没提交"的中间态。
        grant(userId, CreditSource.REGISTER, SIGNUP_BONUS_CREDITS, null,
                "SIGNUP:" + userId, "新用户注册赠送");
    }

    /**
     * 发一批额度。
     *
     * @param expiresAt 过期时间；{@code null} = 永久有效（注册赠送走这条）。
     *                  会员发放传 {@code 发放日 + 30 天}，到期没用完的随批次作废。
     * @param bizKey    发放幂等键，重复发放会被静默跳过。会员用 {@code MEMBERSHIP:{订单号}}，
     *                  管理员调整用 {@code ADMIN:{uuid}}
     * @return 是否真的发了（false = 这个幂等键已经发过）
     */
    @Transactional
    public boolean grant(Long userId, CreditSource source, int amount, LocalDateTime expiresAt,
                         String bizKey, String remark) {
        boolean inserted = batchRepository.insertIgnore(
                userId, source.name(), bizKey, amount, expiresAt, LocalDateTime.now(), remark) > 0;
        if (inserted) {
            log.info("AI额度发放: userId={}, source={}, amount={}, expiresAt={}, bizKey={}",
                    userId, source, amount, expiresAt, bizKey);
        }
        return inserted;
    }

    // ==================== 查询 ====================

    /**
     * 账户可用额度。只读，**不触发注册赠送** —— 补发是写操作，只发生在写路径与
     * {@link #status} 上，免得一个纯查询接口把数据改了。
     */
    @Transactional(readOnly = true)
    public Balance getBalance(Long userId) {
        if (userId == null) {
            return new Balance(0, null);
        }
        int sum = 0;
        LocalDateTime nextExpireAt = null;
        for (AiCreditBatch batch : batchRepository.findUsable(userId, LocalDateTime.now())) {
            sum += batch.getRemainingAmount();
            LocalDateTime expiresAt = batch.getExpiresAt();
            if (expiresAt != null && (nextExpireAt == null || expiresAt.isBefore(nextExpireAt))) {
                nextExpireAt = expiresAt;
            }
        }
        return new Balance(sum, nextExpireAt);
    }

    /**
     * 某张工单上三个功能各自的状态 + 我的账户余额，给前端渲染遮罩/按钮用。
     *
     * @param handlerId 该工单的接单人 id（由调用方查好传进来，避免本类反向依赖 TicketService）
     */
    @Transactional
    public AiQuotaStatusDTO status(Long ticketId, Long userId, Long handlerId) {
        boolean isHandler = userId != null && userId.equals(handlerId);
        if (!isHandler) {
            // 未登录、或登录了但不是这张工单的接单人：不透露余额，也绝不触发补发
            return AiQuotaStatusDTO.builder()
                    .balance(0)
                    .isHandler(false)
                    .analysis(AiFeatureState.NOT_HANDLER)
                    .prd(AiFeatureState.NOT_HANDLER)
                    .chat(AiFeatureState.NOT_HANDLER)
                    .build();
        }

        ensureRegisterGrant(userId);
        Balance balance = getBalance(userId);
        List<AiUsage> slots = usageRepository.findByTicketIdAndActiveSlot(ticketId, ACTIVE_SLOT);

        return AiQuotaStatusDTO.builder()
                .balance(balance.remaining())
                .nextExpireAt(balance.nextExpireAt())
                .isHandler(true)
                .analysis(stateOf(slots, AiFeature.ANALYSIS))
                .prd(stateOf(slots, AiFeature.PRD))
                .chat(stateOf(slots, AiFeature.CHAT))
                .build();
    }

    /** AI 对话是否已解锁。存在的 SUCCESS 占位行就是解锁凭证 */
    @Transactional(readOnly = true)
    public boolean isChatUnlocked(Long ticketId) {
        return usageRepository.findByTicketIdAndFeatureAndActiveSlot(ticketId, AiFeature.CHAT, ACTIVE_SLOT)
                .map(usage -> usage.getStatus() == AiUsageStatus.SUCCESS)
                .orElse(false);
    }

    /**
     * 占位行还在、但状态是 RESERVED 且已经很久没动 → 当初那个进程多半死了，
     * 钱扣了、功能却永远锁在"处理中"。这里把它退掉并释放槽位，让用户能重试。
     *
     * <p>惰性接管而不是定时任务：全仓没有任何 {@code @Scheduled}，为了这件事引入调度
     * 框架（还要保证单实例、还要防止多实例重复执行）不划算，而且正确性不该依赖
     * "某个 job 恰好跑过"。
     */
    @Transactional
    public void reclaimIfStale(Long ticketId, AiFeature feature) {
        usageRepository.findByTicketIdAndFeatureAndActiveSlot(ticketId, feature, ACTIVE_SLOT)
                .filter(usage -> usage.getStatus() == AiUsageStatus.RESERVED)
                .filter(usage -> usage.getUpdatedAt() != null
                        && usage.getUpdatedAt().isBefore(LocalDateTime.now().minus(STALE_AFTER)))
                .ifPresent(usage -> {
                    log.warn("AI 预占超时未结算，自动退款: usageId={}, ticketId={}, feature={}, updatedAt={}",
                            usage.getId(), ticketId, feature, usage.getUpdatedAt());
                    // 同类内自调用：并入当前事务，不必另开（refund 自身也是 @Transactional）
                    refund(usage.getId(), "超时未完成，已自动退款");
                });
    }

    // ==================== 预占与结算 ====================

    /**
     * 预占一次额度：插入占位行 + 从最快过期的批次里扣 1 次。
     *
     * <p>顺序是"先占位、后扣款"，两者在同一个短事务里提交。事务在这里就结束 ——
     * 调用方拿到返回值后才去调 AI（可能几分钟），那是一段没有事务的等待。
     *
     * @throws BusinessException 409 该功能已用过/正在处理中（含并发重复提交）；402 额度不足
     */
    @Transactional
    public AiUsage reserve(Long ticketId, Long userId, AiFeature feature) {
        ensureRegisterGrant(userId);

        // 先把"进程崩在半路"留下的僵尸占位收掉。放在这里是为了三个功能自动都受益 ——
        // 若放到控制器里，就得写三遍，且以后新增功能很容易漏掉。回收与随后的插入同事务，
        // 所以"回收 → 插入"中间不会被其它请求插队。
        reclaimIfStale(ticketId, feature);

        // 先读一次，只是为了给出精确的理由（"用过了"和"正在处理中"该说不同的话）。
        // 这不是权威判定 —— 并发时这里读不到对方尚未提交的行，权威在下面的唯一键插入。
        usageRepository.findByTicketIdAndFeatureAndActiveSlot(ticketId, feature, ACTIVE_SLOT)
                .ifPresent(existing -> {
                    throw new BusinessException(409,
                            existing.getStatus() == AiUsageStatus.RESERVED
                                    ? "该功能正在处理中，请勿重复提交"
                                    : "该工单的「" + label(feature) + "」已经用过了");
                });

        AiUsage usage = new AiUsage();
        usage.setTicketId(ticketId);
        usage.setUserId(userId);
        usage.setFeature(feature);
        usage.setStatus(AiUsageStatus.RESERVED);
        usage.setActiveSlot(ACTIVE_SLOT);
        usage.setAmount(1);
        try {
            // 必须立刻 flush：唯一键冲突要在这个事务里暴露出来，而不是等到提交时才炸
            usageRepository.saveAndFlush(usage);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(409, "该功能正在处理中，请勿重复提交");
        }

        List<AiCreditBatch> batches = batchRepository.lockUsable(userId, LocalDateTime.now());
        if (batches.isEmpty()) {
            // 抛出去 → 外层事务回滚，刚才那行占位一并撤销。
            // 不留"扣了费却没有额度"的残留，也不需要手工清理。
            throw new BusinessException(402, "AI 辅助开发额度不足，开通会员可获得更多次数");
        }

        AiCreditBatch picked = batches.get(0);
        for (AiCreditBatch batch : batches) {
            if (isEarlier(batch.getExpiresAt(), picked.getExpiresAt())) {
                picked = batch;
            }
        }
        picked.setRemainingAmount(picked.getRemainingAmount() - 1);
        batchRepository.save(picked);

        usage.setBatchId(picked.getId());
        usageRepository.save(usage);
        log.info("AI额度预占: usageId={}, ticketId={}, userId={}, feature={}, batchId={}, 剩余={}",
                usage.getId(), ticketId, userId, feature, picked.getId(), picked.getRemainingAmount());
        return usage;
    }

    /** 标记调用成功 —— 额度落定，不再退回。 */
    @Transactional
    public void markSuccess(Long usageId) {
        AiUsage usage = usageRepository.findById(usageId).orElse(null);
        if (usage == null || usage.getStatus() != AiUsageStatus.RESERVED) {
            // 已被 reclaimIfStale 超时接管退款的（AI 跑得比阈值还久）。
            // 此时**不能**把状态改回 SUCCESS：那会让一次已经退款的调用变成"已使用过的功能"。
            log.warn("AI 调用成功但预占行已不在 RESERVED 状态，跳过结算: usageId={}, status={}",
                    usageId, usage == null ? null : usage.getStatus());
            return;
        }
        usage.setStatus(AiUsageStatus.SUCCESS);
        usageRepository.save(usage);
    }

    /**
     * 退回额度并释放槽位（AI 调用失败时唯一会走到这里的地方）。
     *
     * <p>退回到**当初扣的那一批**，而不是重新选一批：否则"先用会员批次、失败后
     * 退回永久批次"会让过期时间悄悄变长。副作用是如果退款时这批已经过期，
     * 退回去的次数也会被惰性过滤挡掉 —— 语义上可接受（那一次本来就该作废）。
     *
     * <p>幂等：状态不是 RESERVED 就直接返回，避免超时接管与失败回调重复退款。
     */
    @Transactional
    public void refund(Long usageId, String reason) {
        AiUsage usage = usageRepository.findById(usageId).orElse(null);
        if (usage == null || usage.getStatus() != AiUsageStatus.RESERVED) {
            return;
        }
        if (usage.getBatchId() != null) {
            batchRepository.findByIdForUpdate(usage.getBatchId()).ifPresent(batch -> {
                batch.setRemainingAmount(batch.getRemainingAmount() + usage.getAmount());
                batchRepository.save(batch);
            });
        }
        usage.setStatus(AiUsageStatus.REFUNDED);
        usage.setActiveSlot(null);
        usage.setFailureReason(truncate(reason));
        usageRepository.save(usage);
        log.info("AI额度已退回: usageId={}, batchId={}, reason={}", usageId, usage.getBatchId(), reason);
    }

    // ==================== 内部小工具 ====================

    private AiFeatureState stateOf(List<AiUsage> slots, AiFeature feature) {
        return slots.stream()
                .filter(usage -> usage.getFeature() == feature)
                .findFirst()
                .map(usage -> usage.getStatus() == AiUsageStatus.RESERVED
                        ? AiFeatureState.IN_PROGRESS
                        : AiFeatureState.USED)
                .orElse(AiFeatureState.AVAILABLE);
    }

    /** NULL 表示永久有效，视为"最晚到期" —— 会员那种会过期的批次要排在它前面先花掉。 */
    private static boolean isEarlier(LocalDateTime candidate, LocalDateTime current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isBefore(current);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= FAILURE_REASON_MAX ? reason : reason.substring(0, FAILURE_REASON_MAX);
    }

    /** 给用户看的名字。前端 tab 上的措辞与这里保持一致 */
    private static String label(AiFeature feature) {
        return switch (feature) {
            case ANALYSIS -> "AI 分析";
            case PRD -> "PRD 生成";
            case CHAT -> "AI 对话";
        };
    }
}
