package com.aiticket.ticket.service;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.exception.BusinessException;
import com.aiticket.ticket.config.AlipayProperties;
import com.aiticket.ticket.dto.MembershipDTO;
import com.aiticket.ticket.dto.MembershipOrderDTO;
import com.aiticket.ticket.dto.MembershipPayDTO;
import com.aiticket.ticket.dto.MembershipPlanDTO;
import com.aiticket.ticket.entity.AiCreditBatch;
import com.aiticket.ticket.entity.MembershipOrder;
import com.aiticket.ticket.entity.UserMembership;
import com.aiticket.ticket.enums.CreditSource;
import com.aiticket.ticket.enums.MembershipOrderStatus;
import com.aiticket.ticket.enums.MembershipPlan;
import com.aiticket.ticket.repository.AiCreditBatchRepository;
import com.aiticket.ticket.repository.MembershipOrderRepository;
import com.aiticket.ticket.repository.UserMembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会员的记账与查询：套餐、我的会员、下单、到账发放、订单查询。
 *
 * <p><b>本类只放能在事务里安全做完的事</b>（数据库读写、发签名链接 —— 后者不发网络请求）。
 * 需要调支付宝网络的编排（回调处理、轮询查单兜底）在
 * {@link MembershipPaymentService}，那里的方法**不带事务**。
 *
 * <p>分开是为了绕开一个很实在的坑：Spring 的 {@code @Transactional} 靠代理生效，
 * 同类内自调用不走代理、事务形同虚设。若把编排也放在本类里，它会自调用
 * {@link #fulfill}，于是"到账"那段就没有事务了；而若把编排标成 {@code @Transactional}，
 * 又会把一次可能好几秒的支付宝网络调用圈进数据库事务里占着连接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    /**
     * 连点「立即开通」的复用窗口。
     *
     * <p>同一用户存在这个窗口内创建、且仍 PENDING 的订单时直接复用，不新建。
     * 理由：连点会建两单，用户可能把两单都付了 —— 虽然多付的钱换来两期会员（不亏），
     * 但界面上会冒出两个待支付订单，解释成本远高于这一行判断。
     *
     * <p>**刻意压到 10 分钟**，短于支付宝那边的交易超时（默认远长于 10 分钟），
     * 所以复用时那笔交易一定还活着，重新生成 payUrl 不会撞到"交易已关闭、不能重开"。
     * 窗口和"不给继续支付"是配套的：10 分钟内算手滑，复用；10 分钟之外算放弃，
     * 那单作废、重新下单。
     */
    private static final Duration REUSE_WINDOW = Duration.ofMinutes(10);

    /** 单号里的随机段长度。6 位 + 精确到秒的时间戳，同秒撞号的概率约百万分之一 */
    private static final int RANDOM_SUFFIX_BOUND = 1_000_000;
    private static final DateTimeFormatter OUT_TRADE_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 撞号重生的次数。唯一键仍是最终裁决，这里只是让常见情况干净 */
    private static final int OUT_TRADE_NO_ATTEMPTS = 3;

    private static final String MEMBERSHIP_STATUS_ACTIVE = "ACTIVE";
    /** 发放幂等键前缀，见 {@link AiQuotaService#grant} */
    private static final String BIZ_KEY_PREFIX = "MEMBERSHIP:";

    private final MembershipOrderRepository orderRepository;
    private final UserMembershipRepository membershipRepository;
    private final AiCreditBatchRepository batchRepository;
    private final AiQuotaService aiQuotaService;
    private final AlipayGateway alipayGateway;
    private final AlipayProperties alipayProperties;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== 读：套餐 / 我的会员 / 订单 ====================

    /**
     * 套餐列表。
     *
     * <p>做成**列表**而不是单个对象：以后加一档只改 {@link MembershipPlan}，前端一行不用动。
     * 需登录才能看，是因为前端只在登录后的会员页/个人主页上渲染它。
     */
    public List<MembershipPlanDTO> listPlans() {
        List<MembershipPlanDTO> plans = new ArrayList<>();
        for (MembershipPlan plan : MembershipPlan.values()) {
            plans.add(MembershipPlanDTO.builder()
                    .planCode(plan.code())
                    .name(plan.displayName())
                    .price(plan.price())
                    .credits(plan.credits())
                    .days(plan.days())
                    .desc(plan.description())
                    .build());
        }
        return plans;
    }

    /**
     * 我的会员状态 + 账户余额。
     *
     * <p><b>isMember 用实时条件判定</b>（{@code status=ACTIVE AND expires_at > now}），
     * 不许读任何缓存字段 —— 否则到期当天会出现"页面说还是会员、次数已经不可用"的矛盾。
     *
     * <p>{@code remaining} 复用 {@link AiQuotaService#getBalance}，**不另写一套 SUM**：
     * 直查 {@code SUM(remaining_amount)} 会把过期批次也算进去，余额于是永远比实际多。
     */
    public MembershipDTO me(Long userId) {
        requireLogin(userId);
        // 顺手补发注册赠送（幂等）。不这么做的话，一个从没碰过 AI 功能的新用户在
        // 会员页上看到的是"还剩 0 次"，而实际上是 5 —— 这个数字必须准。
        aiQuotaService.ensureRegisterGrant(userId);

        AiQuotaService.Balance balance = aiQuotaService.getBalance(userId);
        // findActive 本身不带事务，这里也不需要事务：只有一条 SELECT，
        // 而且"是不是会员"本来就是一次实时判断，不是需要一致快照的多步读。
        Optional<UserMembership> active = currentMembership(userId);

        return MembershipDTO.builder()
                .isMember(active.isPresent())
                .planCode(active.map(UserMembership::getPlanCode).orElse(null))
                .startedAt(active.map(UserMembership::getStartedAt).orElse(null))
                .expiresAt(active.map(UserMembership::getExpiresAt).orElse(null))
                .creditsGranted(active.map(UserMembership::getCreditsGranted).orElse(null))
                .remaining(balance.remaining())
                .nextExpireAt(balance.nextExpireAt())
                .build();
    }

    /** 单笔订单，**纯读库、无侧效应**（惰性关闭与查单兜底在 MembershipPaymentService） */
    public MembershipOrderDTO getOrder(Long userId, String outTradeNo) {
        requireLogin(userId);
        MembershipOrder order = loadOrderOr404(outTradeNo);
        requireOwnership(order, userId);
        return toDTO(order, batchExpiresAt(Set.of(order)));
    }

    /**
     * 我的订单（分页）。
     *
     * <p><b>数据只来自我们库</b>，一列都不去查支付宝：列表页分页查一次要打 N 次支付宝接口，
     * 慢且脆，而且查历史订单本来也不需要它。也**刻意不过滤状态** —— 已关闭的单要如实显示，
     * 用户会以为自己的操作没被记录才会真的慌。
     *
     * <p>查询条件 {@code user_id} 由这里强制取当前登录用户，**不接受前端传** ——
     * 那等于让前端决定能看谁的订单。
     */
    public PageResult<MembershipOrderDTO> listOrders(Long userId, int page, int pageSize) {
        requireLogin(userId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 1) - 1, Math.max(pageSize, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MembershipOrder> orders =
                orderRepository.findByUserIdAndDeletedAtIsNull(userId, pageRequest);

        Map<Long, LocalDateTime> expires = batchExpiresAt(orders.getContent());
        List<MembershipOrderDTO> dtos = orders.getContent().stream()
                .map(o -> toDTO(o, expires))
                .collect(Collectors.toList());
        return PageResult.of(orders.getTotalElements(), page, pageSize, dtos);
    }

    // ==================== 下单 ====================

    /**
     * 创建订单并返回收银台链接。
     *
     * <p>生成的链接里带着**下单那一刻**的 notify-url，所以换了 natapp 域名之后
     * 旧的待支付订单不要再付 —— 重新下单（复用窗口内则由这里重新生成链接，正好覆盖这种情况）。
     *
     * @throws BusinessException 400 未知套餐；401 未登录；503 支付宝密钥没配
     */
    @Transactional
    public MembershipPayDTO createOrder(Long userId, String planCode) {
        requireLogin(userId);
        MembershipPlan plan = MembershipPlan.of(planCode)
                .orElseThrow(() -> new BusinessException(400, "未知的套餐：" + planCode));
        if (!alipayProperties.isConfigured()) {
            // 不配密钥服务照样能跑，只有下单这一步不可用 —— 错误信息要能指路
            log.error("支付宝配置不完整，无法下单。缺少: {}", alipayProperties.missingFields());
            throw new BusinessException(503, "支付功能未配置（缺少 "
                    + alipayProperties.missingFields() + "），请联系管理员");
        }

        MembershipOrder order = reusablePendingOrder(userId, plan);
        if (order == null) {
            order = newOrder(userId, plan);
        } else {
            log.info("复用未支付订单: outTradeNo={}, userId={}", order.getOutTradeNo(), userId);
        }

        String payUrl = alipayGateway.buildPagePayUrl(order.getOutTradeNo(), order.getAmount(), subjectOf(plan));
        return MembershipPayDTO.builder()
                .orderId(order.getId())
                .outTradeNo(order.getOutTradeNo())
                .amount(order.getAmount())
                .payUrl(payUrl)
                .build();
    }

    /** 同一个用户、同一个套餐、10 分钟内创建且仍未支付的单 —— 直接复用而不是新建 */
    private MembershipOrder reusablePendingOrder(Long userId, MembershipPlan plan) {
        LocalDateTime threshold = LocalDateTime.now().minus(REUSE_WINDOW);
        return orderRepository
                .findFirstByUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        userId, MembershipOrderStatus.PENDING)
                // 换套餐不该复用旧单：那张单的权益快照是另一个档位的
                .filter(o -> plan.code().equals(o.getPlanCode()))
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(threshold))
                .orElse(null);
    }

    /**
     * 新插入一张 PENDING 单。
     *
     * <p><b>未支付的单也必须落库</b>，不是"等支付成功再插"：{@code out_trade_no} 要先有
     * 唯一键才谈得上向支付宝下单，防连点的复用逻辑要有行可查，对账要有单据。
     * 而支付宝侧未支付的交易随时可能超时消失，把订单事实寄托在它身上等于没有事实。
     *
     * <p>单号 {@code MEM + yyyyMMddHHmmss + 6 位随机数}，不用自增 id 直接当商户订单号：
     * 将来换渠道或同时挂两个渠道时，"订单号里看不出是什么单"会让对账变成猜谜。
     * 这里先查一次是否已存在（让常见情况不必撞唯一键），但**权威仍是
     * {@code uk_order_out_trade_no}** —— 先查再插在并发下挡不住，只有唯一键能裁决。
     */
    private MembershipOrder newOrder(Long userId, MembershipPlan plan) {
        MembershipOrder order = new MembershipOrder();
        order.setUserId(userId);
        order.setPlanCode(plan.code());
        // 权益快照：以后把套餐从 30 次改成 20 次，这单仍按 30 次发
        order.setPlanCredits(plan.credits());
        order.setPlanDays(plan.days());
        order.setAmount(plan.price());
        order.setPayChannel("ALIPAY_SANDBOX");
        order.setStatus(MembershipOrderStatus.PENDING);
        order.setOutTradeNo(newOutTradeNo());

        MembershipOrder saved = orderRepository.saveAndFlush(order);
        log.info("会员订单已创建: orderId={}, outTradeNo={}, userId={}, plan={}, amount={}",
                saved.getId(), saved.getOutTradeNo(), userId, plan.code(), saved.getAmount().toPlainString());
        return saved;
    }

    private String newOutTradeNo() {
        String timestamp = LocalDateTime.now().format(OUT_TRADE_NO_TIME);
        for (int attempt = 0; attempt < OUT_TRADE_NO_ATTEMPTS; attempt++) {
            String candidate = "MEM" + timestamp + String.format("%06d", RANDOM.nextInt(RANDOM_SUFFIX_BOUND));
            if (orderRepository.findByOutTradeNoAndDeletedAtIsNull(candidate).isEmpty()) {
                return candidate;
            }
            log.warn("商户订单号撞号，重新生成: {}", candidate);
        }
        throw new BusinessException(500, "生成商户订单号失败，请稍后重试");
    }

    private static String subjectOf(MembershipPlan plan) {
        return "DevMatch " + plan.displayName() + "（" + plan.days() + " 天 " + plan.credits() + " 次）";
    }

    // ==================== 到账（本类的核心） ====================

    /**
     * 把一张订单标记为已支付，并发放会员权益。异步回调与主动查单兜底**都调它**。
     *
     * <p>顺序：<b>锁订单行 → 判定是否已到账 → 算新周期起点 → 插会员行 → 发次数 → 改单</b>，
     * 全部在同一个事务里。{@code aiQuotaService.grant} 是跨 bean 调用，默认 REQUIRED，
     * 会并入当前事务，不会另开一个 —— 所以不存在"发了次数但会员行没插上"的中间态。
     *
     * <p><b>守卫只能挡 PAID，绝不能写成 {@code status != PENDING → 返回}。</b>
     * 我们会主动把超时单标成 CLOSED，而支付宝那边的交易往往还活着（我们的判据阈值比
     * 支付宝的交易超时短得多）。用户完全可能从支付宝的交易列表里把旧单付掉 ——
     * 那时回调带着 TRADE_SUCCESS 到达，若把 CLOSED 挡掉，这一单就**钱收了、次数不发**，
     * 而且日志里一片安静（回调处理"成功"返回了 success）。这一条同时盖住"重复回调"
     * 和"迟到的支付"两种情况，幂等交给本方法的行锁 + biz_key 唯一键。
     *
     * @return 本次是否真的发了（false = 订单不存在或已到账，什么都没做）
     */
    @Transactional
    public boolean fulfill(String outTradeNo, String tradeNo) {
        MembershipOrder order = orderRepository.findByOutTradeNoForUpdate(outTradeNo).orElse(null);
        if (order == null) {
            log.error("到账失败：商户订单号查不到订单。outTradeNo={}", outTradeNo);
            return false;
        }
        if (order.getStatus() == MembershipOrderStatus.PAID) {
            log.info("订单已到账，跳过重复处理: outTradeNo={}", outTradeNo);
            return false;
        }

        Long userId = order.getUserId();
        LocalDateTime now = LocalDateTime.now();

        // 用户级行锁。**这个锁不是给"重复回调"用的**（那个上面那道已经挡了），
        // 它挡的是"用户同时买两期"：没有它，两个请求会各自算出同一个起点，
        // 插出两段完全重叠的会员期 —— 用户付了两次钱，只拿到 30 天。
        // 这是正常场景（额度用完了会员还没到期，应该允许再买一份），不是异常路径。
        List<UserMembership> existing = membershipRepository.lockByUserId(userId);
        LocalDateTime base = now;
        for (UserMembership m : existing) {
            if (!MEMBERSHIP_STATUS_ACTIVE.equals(m.getStatus()) || m.getExpiresAt() == null) {
                continue;
            }
            // 提前续费不白扔剩余天数：新周期从旧的到期日往后接，而不是从今天算
            if (m.getExpiresAt().isAfter(now) && m.getExpiresAt().isAfter(base)) {
                base = m.getExpiresAt();
            }
        }
        // 批次和会员行用**同一个** expiresAt，这样"到期后没用完的次数作废"自然成立
        // （批次一过期，findUsable 就不再返回它），不需要任何核销逻辑
        LocalDateTime expiresAt = base.plusDays(order.getPlanDays());

        UserMembership membership = new UserMembership();
        membership.setUserId(userId);
        membership.setPlanCode(order.getPlanCode());
        membership.setStatus(MEMBERSHIP_STATUS_ACTIVE);
        membership.setCreditsGranted(order.getPlanCredits());
        membership.setStartedAt(base);
        membership.setExpiresAt(expiresAt);
        membership.setAutoRenew(false);
        membershipRepository.save(membership);

        // 幂等键用 orderId：重复到账只会留下一批次数（uk_batch_biz_key + INSERT IGNORE）
        aiQuotaService.grant(userId, CreditSource.MEMBERSHIP, order.getPlanCredits(), expiresAt,
                BIZ_KEY_PREFIX + order.getId(), "会员开通");

        order.setStatus(MembershipOrderStatus.PAID);
        order.setTradeNo(tradeNo);
        order.setPaidAt(now);
        orderRepository.save(order);

        log.info("会员已开通: orderId={}, userId={}, plan={}, 周期={} ~ {}, 发放={} 次",
                order.getId(), userId, order.getPlanCode(), base, expiresAt, order.getPlanCredits());
        return true;
    }

    /**
     * 把仍为 PENDING 的单标成 CLOSED。**只做这一件事**，供
     * {@link MembershipPaymentService} 在"支付宝侧确认没戏"时调用。
     *
     * <p>判据必须来自支付宝的查单结果（交易已关闭，或压根查不到这笔交易），
     * **不能是"创建超过 N 分钟就关"** —— 按时硬关会直接制造出"钱收了、次数不发"的场景。
     */
    @Transactional
    public boolean closeIfPending(String outTradeNo) {
        MembershipOrder order = orderRepository.findByOutTradeNoForUpdate(outTradeNo).orElse(null);
        if (order == null || order.getStatus() != MembershipOrderStatus.PENDING) {
            return false;
        }
        order.setStatus(MembershipOrderStatus.CLOSED);
        orderRepository.save(order);
        log.info("会员订单已关闭（支付宝侧确认未成交）: outTradeNo={}", outTradeNo);
        return true;
    }

    // ==================== 给 MembershipPaymentService 用的小接口 ====================

    /** 按单号读订单，查不到就 404。不锁、不判归属 —— 归属由调用方按当前用户判 */
    public MembershipOrder loadOrderOr404(String outTradeNo) {
        return orderRepository.findByOutTradeNoAndDeletedAtIsNull(outTradeNo)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
    }

    /**
     * 只能看自己的单。
     *
     * <p>403 而不是 404：这是别人的资源但**确实存在**，与全仓 404 → 401 → 403 的错误码
     * 顺序一致（见 TicketService.requireParticipantTicket 的注释）。
     */
    public void requireOwnership(MembershipOrder order, Long userId) {
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(403, "只能查看自己的订单");
        }
    }

    /** 读回最新状态并转 DTO（供编排层在查单/到账之后取最终结果） */
    public MembershipOrderDTO reload(String outTradeNo) {
        MembershipOrder order = loadOrderOr404(outTradeNo);
        return toDTO(order, batchExpiresAt(Set.of(order)));
    }

    public void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    /** 该用户当前有效的会员周期，取到期最晚的一条 */
    private Optional<UserMembership> currentMembership(Long userId) {
        return membershipRepository.findActive(userId, LocalDateTime.now()).stream()
                .max(Comparator.comparing(UserMembership::getExpiresAt));
    }

    // ==================== DTO 组装 ====================

    /**
     * 把订单转成 DTO，并补上 {@code expiresAt}。
     *
     * <p>会员到期日不存订单表，而是从这次发放对应的额度批次上取
     * （{@code biz_key = MEMBERSHIP:{orderId}}）—— 那也是 {@code fulfill} 真正发出去的
     * 那个日期，比"查用户当前会员"更准（后者在会员已过期时会返回空）。
     * 一次批量查出来，避免列表页 N+1。
     */
    private MembershipOrderDTO toDTO(MembershipOrder order, Map<Long, LocalDateTime> expiresByOrderId) {
        return MembershipOrderDTO.builder()
                .orderId(order.getId())
                .outTradeNo(order.getOutTradeNo())
                .planCode(order.getPlanCode())
                .planCredits(order.getPlanCredits())
                .planDays(order.getPlanDays())
                .amount(order.getAmount())
                .status(order.getStatus().name())
                .paidAt(order.getPaidAt())
                .expiresAt(expiresByOrderId.get(order.getId()))
                .createdAt(order.getCreatedAt())
                .build();
    }

    /** 批量取"这批订单各自发放的那个批次"的到期日，键是订单 id */
    private Map<Long, LocalDateTime> batchExpiresAt(Collection<MembershipOrder> orders) {
        Set<String> keys = orders.stream()
                .map(o -> BIZ_KEY_PREFIX + o.getId())
                .collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDateTime> result = new HashMap<>();
        for (AiCreditBatch batch : batchRepository.findByBizKeyIn(keys)) {
            String key = batch.getBizKey();
            // biz_key 形如 MEMBERSHIP:{orderId}，前缀长度固定，直接切
            try {
                Long orderId = Long.valueOf(key.substring(BIZ_KEY_PREFIX.length()));
                result.put(orderId, batch.getExpiresAt());
            } catch (NumberFormatException e) {
                log.warn("额度批次的幂等键格式异常，无法解析出订单号: bizKey={}", key);
            }
        }
        return result;
    }

    /** 还没被用到的金额工具：给编排层做"回调金额与订单金额一致"的比对 */
    public static boolean sameAmount(BigDecimal a, BigDecimal b) {
        // 必须是 compareTo：30 元在支付宝那边回传的是 "30.00"，标度不同，
        // equals 与字符串比都会判成不相等
        return a != null && b != null && a.compareTo(b) == 0;
    }
}
