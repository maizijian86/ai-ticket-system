package com.aiticket.ticket.service;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.exception.BusinessException;
import com.aiticket.ticket.config.AlipayProperties;
import com.aiticket.ticket.dto.UserWalletDTO;
import com.aiticket.ticket.dto.WalletOrderDTO;
import com.aiticket.ticket.dto.WalletPayDTO;
import com.aiticket.ticket.dto.WalletTxDTO;
import com.aiticket.ticket.entity.Ticket;
import com.aiticket.ticket.entity.TicketEscrow;
import com.aiticket.ticket.entity.UserWallet;
import com.aiticket.ticket.entity.WalletOrder;
import com.aiticket.ticket.entity.WalletTransaction;
import com.aiticket.ticket.enums.EscrowStatus;
import com.aiticket.ticket.enums.WalletOrderStatus;
import com.aiticket.ticket.enums.WalletTxType;
import com.aiticket.ticket.repository.TicketEscrowRepository;
import com.aiticket.ticket.repository.TicketRepository;
import com.aiticket.ticket.repository.UserWalletRepository;
import com.aiticket.ticket.repository.WalletOrderRepository;
import com.aiticket.ticket.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 钱包与工单资金托管。
 *
 * <p>一句话概括资金模型：**发布时冻结、验收时结算、关闭时退回**。
 * 冻结是必须的 —— 如果"发布时只校验余额、完成时才扣款"，那么同一笔钱可以发出
 * 任意多张单（余额列在两次校验之间没有任何变化），最终只有第一张能扣成功，
 * 其余接单者白干。
 *
 * <p><b>三条不变量</b>（对账 SQL 会逐条检查，见 README / 开发文档 §10）：
 * <pre>
 *   user_wallet.available_amount = SUM(wallet_transaction.amount)
 *   user_wallet.frozen_amount    = SUM(wallet_transaction.frozen_delta)
 *   user_wallet.frozen_amount    = SUM(ticket_escrow.amount WHERE status='FROZEN')
 * </pre>
 * 为了让它们天然成立，**所有金额变动都必须经过本类的 {@link #move} 一个出口** ——
 * 不要在外面直接 setAvailableAmount / setFrozenAmount。
 *
 * <p><b>金额只允许整数元</b>（1 ~ 50000），见 {@link #requireValidAmount}。
 * 这条规则一次性消灭了"三位小数被 DECIMAL(12,2) 静默截断 → 支付宝收 1.005 →
 * 金额比对失败 → 钱付了余额不加"这一整类坑。
 *
 * <p><b>事务</b>：本类所有写方法都是 {@code @Transactional}，并且都是**短事务**
 *（不碰网络）。调支付宝的代码在 {@code WalletPaymentService}，那里刻意不加事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    /** 金额下限 / 上限（元，整数）。充值、工单预算共用这一套 */
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("1");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("50000");

    private static final String REF_TICKET = "TICKET";
    /** 充值流水用这个，与 TICKET 分开，避免工单 id 与订单 id 撞号后语义混淆 */
    private static final String REF_WALLET_ORDER = "WALLET_ORDER";

    /** 防连点：同金额未支付单在这个窗口内直接复用，而不是再开一个收银台 */
    private static final Duration REUSE_WINDOW = Duration.ofMinutes(10);
    private static final DateTimeFormatter OUT_TRADE_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_SUFFIX_BOUND = 1_000_000;
    private static final int OUT_TRADE_NO_ATTEMPTS = 5;
    private static final String OUT_TRADE_NO_PREFIX = "WAL";

    /**
     * 充值流水的"轮次"固定写 0。
     *
     * <p>工单托管的轮次从 1 开始，所以两者不会冲突；而写 0（非 NULL）之后，
     * 唯一键 {@code (ref_type, ref_id, escrow_round, type)} 就能挡住"同一笔充值被
     * 加了两次钱"。若这里留 NULL，MySQL 的唯一索引不约束该行，重复回调会直接加两次。
     */
    private static final Integer RECHARGE_ROUND = 0;

    private final UserWalletRepository walletRepository;
    private final TicketEscrowRepository escrowRepository;
    private final WalletTransactionRepository txRepository;
    private final WalletOrderRepository walletOrderRepository;
    private final TicketRepository ticketRepository;
    private final AlipayGateway alipayGateway;
    private final AlipayProperties alipayProperties;

    // ==================== 读 ====================

    /**
     * 我的钱包。**没有行就懒创建** —— 不在登录时批量插，
     * 否则每个从不涉及钱的账号都会留一行空数据。
     */
    @Transactional
    public UserWalletDTO me(Long userId) {
        requireLogin(userId);
        UserWallet w = walletOrCreate(userId);
        return UserWalletDTO.builder()
                .availableAmount(w.getAvailableAmount())
                .frozenAmount(w.getFrozenAmount())
                .totalAmount(w.totalAmount())
                .minRecharge(MIN_AMOUNT)
                .maxRecharge(MAX_AMOUNT)
                .build();
    }

    // ==================== 资金动作 ====================

    /**
     * 发布工单：把预算从可用余额转进冻结，并建一条托管记录。
     *
     * <p>与工单落库在**同一个事务**（由 {@code TicketService.createTicket} 调用），
     * 任一步失败一起回滚 —— 不会出现"钱冻住了但工单没建出来"。
     *
     * @return 新建的托管行
     * @throws BusinessException 402 余额不足（附带差额，前端可提示充值）
     */
    @Transactional
    public TicketEscrow freezeForTicket(Long userId, Long ticketId, BigDecimal amount) {
        requireLogin(userId);
        requireValidAmount(amount, "预算");

        UserWallet wallet = walletForUpdate(userId);
        if (wallet.getAvailableAmount().compareTo(amount) < 0) {
            BigDecimal shortfall = amount.subtract(wallet.getAvailableAmount());
            throw new BusinessException(402,
                    "余额不足，还差 " + shortfall.toPlainString() + " 元，请先充值");
        }

        int round = escrowRepository.maxRound(ticketId) + 1;
        move(wallet, WalletTxType.FREEZE, amount.negate(), amount,
                ticketId, round, "发布工单，托管预算");

        TicketEscrow escrow = new TicketEscrow();
        escrow.setTicketId(ticketId);
        escrow.setRound(round);
        escrow.setPublisherId(userId);
        escrow.setAmount(amount);
        escrow.setStatus(EscrowStatus.FROZEN);
        TicketEscrow saved = escrowRepository.save(escrow);

        log.info("托管已建立: ticketId={}, round={}, publisher={}, amount={}",
                ticketId, round, userId, amount.toPlainString());
        return saved;
    }

    /**
     * 未接单时改预算：只转移差额，不重新冻结全额。
     *
     * <p><b>天然幂等</b>：差额以 {@code escrow.amount} 为基准计算，
     * 重复提交同一个值 → 差额 0 → 空操作（不写流水）。
     *
     * <p>加钱时余额不足会抛 402 并让**整笔编辑失败** —— 调用方（updateTicket）
     * 不要再吞掉这个异常去"部分保存"，否则用户会看到"标题改了、预算没改"的错位状态。
     */
    @Transactional
    public void adjustEscrow(Long ticketId, BigDecimal newAmount) {
        requireValidAmount(newAmount, "预算");

        TicketEscrow escrow = escrowRepository.findFrozenForUpdate(ticketId)
                .orElseThrow(() -> new BusinessException(400, "该工单没有托管中的资金，不能修改预算"));

        BigDecimal delta = newAmount.subtract(escrow.getAmount());
        if (delta.signum() == 0) {
            return; // 值没变：空操作，也不写流水
        }

        UserWallet wallet = walletForUpdate(escrow.getPublisherId());
        if (delta.signum() > 0) {
            if (wallet.getAvailableAmount().compareTo(delta) < 0) {
                BigDecimal shortfall = delta.subtract(wallet.getAvailableAmount());
                throw new BusinessException(402,
                        "余额不足，调高预算还差 " + shortfall.toPlainString() + " 元");
            }
            // 加钱：可用 -delta，冻结 +delta
            move(wallet, WalletTxType.ESCROW_ADJUST, delta.negate(), delta,
                    ticketId, escrow.getRound(), "改预算，追加托管");
        } else {
            // 减钱：可用 +|delta|，冻结 -|delta|
            move(wallet, WalletTxType.ESCROW_ADJUST, delta.negate(), delta,
                    ticketId, escrow.getRound(), "改预算，退回差额");
        }

        escrow.setAmount(newAmount);
        escrowRepository.save(escrow);
        log.info("托管额已调整: ticketId={}, round={}, {} -> {}",
                ticketId, escrow.getRound(), newAmount.subtract(delta).toPlainString(),
                newAmount.toPlainString());
    }

    /**
     * 工单关闭 / 删除：托管全额退回发布者的可用余额。
     *
     * @return 是否真的退了（false = 该工单没有托管资金，例如钱包上线前创建的历史单）
     */
    @Transactional
    public boolean refundTicketEscrow(Long ticketId) {
        TicketEscrow escrow = escrowRepository.findFrozenForUpdate(ticketId).orElse(null);
        if (escrow == null) {
            // 历史工单：创建于钱包上线之前，从来没冻结过钱。不抛异常 ——
            // 抛了会让这些单永远关不掉。
            log.info("工单无托管资金，跳过退回: ticketId={}", ticketId);
            return false;
        }

        BigDecimal amount = escrow.getAmount();
        escrow.setStatus(EscrowStatus.REFUNDED);
        escrowRepository.save(escrow);

        UserWallet wallet = walletForUpdate(escrow.getPublisherId());
        move(wallet, WalletTxType.REFUND, amount, amount.negate(),
                ticketId, escrow.getRound(), "工单已关闭，托管退回");

        log.info("托管已退回: ticketId={}, round={}, publisher={}, amount={}",
                ticketId, escrow.getRound(), escrow.getPublisherId(), amount.toPlainString());
        return true;
    }

    /**
     * 验收通过：钱从发布者的冻结转入接单者的可用余额。
     *
     * <p><b>金额只能从这里内部读，不接受参数</b> —— 这样调用方没有机会误传
     * {@code ticket.getBudget()}。发布者在未接单时可以改预算，改完之后
     * {@code ticket.budget} 与已冻结的金额就不是一回事了，读错就会少结算。
     *
     * <p>另外还会交叉验证 {@code escrow.amount} 与流水推算出的累计冻结额，
     * 不一致说明有代码路径漏记账 —— 此时宁可报错，也不要照着一方动钱。
     *
     * @return 是否真的结算了（false = 没有托管资金，历史工单）
     */
    @Transactional
    public boolean settleTicket(Long ticketId) {
        TicketEscrow escrow = escrowRepository.findFrozenForUpdate(ticketId).orElse(null);
        if (escrow == null) {
            log.info("工单无托管资金，跳过结算: ticketId={}", ticketId);
            return false;
        }

        BigDecimal amount = escrow.getAmount();

        // 交叉验证：流水里这一轮累计押的钱应该正好是 escrow.amount
        BigDecimal lockedNegated = txRepository.sumLockedAmount(ticketId, escrow.getRound());
        if (lockedNegated.negate().compareTo(amount) != 0) {
            log.error("托管额与流水不一致，拒绝结算: ticketId={}, round={}, escrow={}, ledger={}",
                    ticketId, escrow.getRound(), amount.toPlainString(),
                    lockedNegated.negate().toPlainString());
            throw new BusinessException(500, "该工单的资金记录不一致，请联系管理员");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(404, "工单不存在"));
        Long handlerId = ticket.getHandlerId();
        if (handlerId == null) {
            throw new BusinessException(500, "该工单没有接单人，无法结算");
        }

        escrow.setStatus(EscrowStatus.SETTLED);
        escrowRepository.save(escrow);

        // 两个钱包按 userId 升序加锁，避免并发结算时互相死锁
        UserWallet publisher;
        UserWallet handler;
        if (escrow.getPublisherId() <= handlerId) {
            publisher = walletForUpdate(escrow.getPublisherId());
            handler = walletForUpdate(handlerId);
        } else {
            handler = walletForUpdate(handlerId);
            publisher = walletForUpdate(escrow.getPublisherId());
        }

        // 发布者：可用不变，冻结减少（这条流水的 amount 是 0，金额体现在 frozen_delta 上）
        move(publisher, WalletTxType.SETTLE_OUT, BigDecimal.ZERO, amount.negate(),
                ticketId, escrow.getRound(), "验收通过，托管结算给接单者");
        // 接单者：可用增加
        move(handler, WalletTxType.SETTLE_IN, amount, BigDecimal.ZERO,
                ticketId, escrow.getRound(), "验收通过，收到工单款项");

        log.info("托管已结算: ticketId={}, round={}, publisher={}, handler={}, amount={}",
                ticketId, escrow.getRound(), escrow.getPublisherId(), handlerId, amount.toPlainString());
        return true;
    }

    // ==================== 充值 ====================

    /**
     * 创建充值订单并签发收银台链接。
     *
     * <p>金额规则与工单预算共用 {@link #requireValidAmount}：整数元、1 ~ 50000。
     *
     * <p>**未支付的单也要落库**（不是"等支付成功再插"）：{@code out_trade_no} 要先有
     * 唯一键才谈得上向支付宝下单、防连点的复用逻辑要有行可查、对账要有单据。
     */
    @Transactional
    public WalletPayDTO createRechargeOrder(Long userId, BigDecimal amount) {
        requireLogin(userId);
        requireValidAmount(amount, "充值金额");

        if (!alipayProperties.isWalletConfigured()) {
            // 不配密钥服务照样能跑，只有充值不可用 —— 错误信息要能指路
            log.error("支付宝充值配置不完整，无法下单。缺少: {}", alipayProperties.missingWalletFields());
            throw new BusinessException(503, "充值功能未配置（缺少 "
                    + alipayProperties.missingWalletFields() + "），请联系管理员");
        }

        WalletOrder order = reusablePendingOrder(userId, amount);
        if (order == null) {
            order = newOrder(userId, amount);
        } else {
            log.info("复用未支付的充值单: outTradeNo={}, userId={}, amount={}",
                    order.getOutTradeNo(), userId, order.getAmount().toPlainString());
        }

        String subject = "DevMatch 钱包充值 " + order.getAmount().toPlainString() + " 元";
        String payUrl = alipayGateway.buildPagePayUrl(
                order.getOutTradeNo(), order.getAmount(), subject,
                alipayProperties.getWalletNotifyUrl(), alipayProperties.getWalletReturnUrl());

        return WalletPayDTO.builder()
                .orderId(order.getId())
                .outTradeNo(order.getOutTradeNo())
                .amount(order.getAmount())
                .payUrl(payUrl)
                .build();
    }

    /**
     * 充值到账（回调 / 查单兜底共用）。
     *
     * <p><b>守卫只能挡 PAID，绝不能写成 {@code status != PENDING → 返回}。</b>
     * 我们会主动把超时单标成 CLOSED，而支付宝那边的交易往往还活着（我们判据阈值比
     * 支付宝的交易超时短得多）。用户完全可能从支付宝的交易列表里把旧单付掉 ——
     * 那时回调带着 TRADE_SUCCESS 到达，若把 CLOSED 挡掉，这一单就**钱收了、余额不加**，
     * 而且日志里一片安静（回调"成功"返回了 success）。
     *
     * <p>幂等由三重保障：订单行锁 + 状态守卫 + 流水唯一键
     * {@code (WALLET_ORDER, orderId, 0, RECHARGE)}。
     *
     * @return 本次是否真的加了钱（false = 订单不存在或已到账）
     */
    @Transactional
    public boolean fulfillRecharge(String outTradeNo, String tradeNo) {
        WalletOrder order = walletOrderRepository.findByOutTradeNoForUpdate(outTradeNo).orElse(null);
        if (order == null) {
            log.error("充值到账失败：商户订单号查不到订单。outTradeNo={}", outTradeNo);
            return false;
        }
        if (order.getStatus() == WalletOrderStatus.PAID) {
            log.info("充值单已到账，跳过重复处理: outTradeNo={}", outTradeNo);
            return false;
        }

        // 流水已存在但订单还是 PENDING（例如上次提交后订单状态没写上）：
        // 只补状态，不再加钱。唯一键是权威，这里只是让日志更好读。
        if (txRepository.existsByRefTypeAndRefIdAndEscrowRoundAndType(
                REF_WALLET_ORDER, order.getId(), RECHARGE_ROUND, WalletTxType.RECHARGE)) {
            log.warn("充值流水已存在，仅补订单状态: outTradeNo={}, orderId={}", outTradeNo, order.getId());
            markPaid(order, tradeNo);
            return false;
        }

        UserWallet wallet = walletForUpdate(order.getUserId());
        BigDecimal amount = order.getAmount();
        move(wallet, WalletTxType.RECHARGE, amount, BigDecimal.ZERO,
                REF_WALLET_ORDER, order.getId(), RECHARGE_ROUND, "支付宝充值到账");
        markPaid(order, tradeNo);

        log.info("充值到账: outTradeNo={}, userId={}, amount={}, 余额={}",
                outTradeNo, order.getUserId(), amount.toPlainString(),
                wallet.getAvailableAmount().toPlainString());
        return true;
    }

    /**
     * 把仍为 PENDING 的充值单标成 CLOSED。**只做这一件事**。
     *
     * <p>判据必须来自支付宝的查单结果（交易已关闭，或压根查不到这笔交易），
     * **不能是"创建超过 N 分钟就关"** —— 按时硬关会制造出"钱收了、余额不加"的场景。
     */
    @Transactional
    public boolean closeRechargeIfPending(String outTradeNo) {
        WalletOrder order = walletOrderRepository.findByOutTradeNoForUpdate(outTradeNo).orElse(null);
        if (order == null || order.getStatus() != WalletOrderStatus.PENDING) {
            return false;
        }
        order.setStatus(WalletOrderStatus.CLOSED);
        walletOrderRepository.save(order);
        log.info("充值单已关闭（支付宝侧确认未成交）: outTradeNo={}", outTradeNo);
        return true;
    }

    /** 按单号读订单，查不到就 404。不锁、不判归属 —— 归属由调用方按当前用户判 */
    public WalletOrder loadOrderOr404(String outTradeNo) {
        return walletOrderRepository.findByOutTradeNoAndDeletedAtIsNull(outTradeNo)
                .orElseThrow(() -> new BusinessException(404, "充值订单不存在"));
    }

    /** 只能看自己的单 */
    public void requireOwnership(WalletOrder order, Long userId) {
        requireLogin(userId);
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(403, "无权查看他人的充值订单");
        }
    }

    /** 单笔状态（结果页轮询后的重新读取） */
    public WalletOrderDTO orderDto(String outTradeNo) {
        return toDTO(loadOrderOr404(outTradeNo));
    }

    /** 我的充值记录（分页）。**只读我们库**，不查支付宝 */
    @Transactional(readOnly = true)
    public PageResult<WalletOrderDTO> listOrders(Long userId, int page, int pageSize) {
        requireLogin(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        Page<WalletOrder> result = walletOrderRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        userId, PageRequest.of(safePage - 1, safeSize));
        List<WalletOrderDTO> dtos = result.getContent().stream().map(this::toDTO).toList();
        return PageResult.of(result.getTotalElements(), safePage, safeSize, dtos);
    }

    /**
     * 我的资金流水（分页）。这是**审计账本**，只增不改 ——
     * 用户可以拿它对"钱到底是怎么变成现在这个数的"。
     */
    @Transactional(readOnly = true)
    public PageResult<WalletTxDTO> listTransactions(Long userId, int page, int pageSize) {
        requireLogin(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        Page<WalletTransaction> result = txRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(safePage - 1, safeSize));
        List<WalletTxDTO> dtos = result.getContent().stream()
                .map(tx -> WalletTxDTO.builder()
                        .id(tx.getId())
                        .type(tx.getType() == null ? null : tx.getType().name())
                        .amount(tx.getAmount())
                        .frozenDelta(tx.getFrozenDelta())
                        .balanceAfter(tx.getBalanceAfter())
                        .refType(tx.getRefType())
                        .refId(tx.getRefId())
                        .remark(tx.getRemark())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .toList();
        return PageResult.of(result.getTotalElements(), safePage, safeSize, dtos);
    }

    // ==================== 内部 ====================

    private void markPaid(WalletOrder order, String tradeNo) {
        order.setStatus(WalletOrderStatus.PAID);
        order.setTradeNo(tradeNo);
        order.setPaidAt(LocalDateTime.now());
        walletOrderRepository.save(order);
    }

    /** 同一个用户、同一个金额、10 分钟内创建且仍未支付的单 —— 直接复用（防连点开多个收银台） */
    private WalletOrder reusablePendingOrder(Long userId, BigDecimal amount) {
        LocalDateTime threshold = LocalDateTime.now().minus(REUSE_WINDOW);
        return walletOrderRepository
                .findFirstByUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                        userId, WalletOrderStatus.PENDING)
                // 金额不同不能复用：那张单的 amount 是另一个数
                .filter(o -> o.getAmount().compareTo(amount) == 0)
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(threshold))
                .orElse(null);
    }

    /**
     * 新插入一张 PENDING 单。
     *
     * <p>单号 {@code WAL + yyyyMMddHHmmss + 6 位随机数}。不用自增 id 直接当商户订单号：
     * 将来换渠道或同时挂两个渠道时，"订单号里看不出是什么单"会让对账变成猜谜。
     * 先查一次只是让常见情况不必撞唯一键，**权威仍是 {@code uk_wallet_order_out_trade_no}**。
     */
    private WalletOrder newOrder(Long userId, BigDecimal amount) {
        WalletOrder order = new WalletOrder();
        order.setUserId(userId);
        order.setAmount(amount);
        order.setPayChannel("ALIPAY_SANDBOX");
        order.setStatus(WalletOrderStatus.PENDING);
        order.setOutTradeNo(newOutTradeNo());

        WalletOrder saved = walletOrderRepository.saveAndFlush(order);
        log.info("充值订单已创建: orderId={}, outTradeNo={}, userId={}, amount={}",
                saved.getId(), saved.getOutTradeNo(), userId, amount.toPlainString());
        return saved;
    }

    private String newOutTradeNo() {
        String timestamp = LocalDateTime.now().format(OUT_TRADE_NO_TIME);
        for (int attempt = 0; attempt < OUT_TRADE_NO_ATTEMPTS; attempt++) {
            String candidate = OUT_TRADE_NO_PREFIX + timestamp
                    + String.format("%06d", RANDOM.nextInt(RANDOM_SUFFIX_BOUND));
            if (walletOrderRepository.findByOutTradeNoAndDeletedAtIsNull(candidate).isEmpty()) {
                return candidate;
            }
            log.warn("充值商户订单号撞号，重新生成: {}", candidate);
        }
        throw new BusinessException(500, "生成充值订单号失败，请稍后重试");
    }

    private WalletOrderDTO toDTO(WalletOrder order) {
        return WalletOrderDTO.builder()
                .id(order.getId())
                .outTradeNo(order.getOutTradeNo())
                .amount(order.getAmount())
                .status(order.getStatus() == null ? null : order.getStatus().name())
                .tradeNo(order.getTradeNo())
                .paidAt(order.getPaidAt())
                .createdAt(order.getCreatedAt())
                .build();
    }

    // ==================== 校验 ====================

    /**
     * 金额规则：**整数元，1 ~ 50000**。充值金额与工单预算共用。
     *
     * <p>判断小数用 {@code stripTrailingZeros().scale() > 0}：
     * <ul>
     *   <li>{@code 1.5} → scale 1 → 拒绝</li>
     *   <li>{@code 1.50} → 归一成 {@code 1.5} → scale 1 → 拒绝（不会被"两位小数"混过去）</li>
     *   <li>{@code 100} / {@code 100.00} → 归一成 {@code 1E+2} → scale -2 → 通过</li>
     * </ul>
     * 所以不能写成 {@code scale() > 2}。
     *
     * <p><b>先判小数、再判范围</b>：反过来的话 {@code 0.5} 会先撞"至少要 1 元"，
     * 用户会以为"那填 1 就行"，而真正的问题是它带了小数。
     */
    public static void requireValidAmount(BigDecimal amount, String label) {
        if (amount == null) {
            throw new BusinessException(400, "请填写" + label);
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(400, label + "只能是整数元（不支持角、分）");
        }
        if (amount.compareTo(MIN_AMOUNT) < 0) {
            throw new BusinessException(400, label + "最少 " + MIN_AMOUNT.toPlainString() + " 元");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(400, label + "最多 " + MAX_AMOUNT.toPlainString() + " 元");
        }
    }

    public static void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    // ==================== 对账（只读，不修） ====================

    /** 对账结果。三个字段都为 0 才算平账 */
    public record Reconcile(UserWallet wallet,
                            BigDecimal ledgerAvailable, BigDecimal ledgerFrozen,
                            BigDecimal escrowFrozen) {

        public boolean balanced() {
            return wallet.getAvailableAmount().compareTo(ledgerAvailable) == 0
                    && wallet.getFrozenAmount().compareTo(ledgerFrozen) == 0
                    && wallet.getFrozenAmount().compareTo(escrowFrozen) == 0;
        }
    }

    /**
     * 三向对账：钱包行 vs 流水 vs 托管表。
     *
     * <p><b>只读，不自动修复</b> —— 自动修会掩盖真正的 bug，
     * 让"钱算错了"变成"钱悄悄被改回去了"。
     */
    @Transactional(readOnly = true)
    public Reconcile reconcile(Long userId) {
        UserWallet wallet = walletRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "该用户还没有钱包"));
        Reconcile r = new Reconcile(
                wallet,
                txRepository.sumAmountByUser(userId),
                txRepository.sumFrozenDeltaByUser(userId),
                escrowRepository.sumFrozenByPublisher(userId));
        if (!r.balanced()) {
            log.error("钱包对账不平: userId={}, wallet(available={}, frozen={}), "
                            + "ledger(available={}, frozen={}), escrowFrozen={}",
                    userId, wallet.getAvailableAmount(), wallet.getFrozenAmount(),
                    r.ledgerAvailable(), r.ledgerFrozen(), r.escrowFrozen());
        }
        return r;
    }

    // ==================== 内部 ====================

    /**
     * 所有金额变动的**唯一出口**。
     *
     * <p>在这里一次性完成：改两个余额池 → 落库 → 写流水（带变动后的余额快照）。
     * 任何新加的赚钱/花钱逻辑都必须走它，否则对账恒等式会破。
     *
     * @param availableDelta 可用余额变动（正=增加）
     * @param frozenDelta    冻结额变动（正=转入托管）
     */
    private void move(UserWallet wallet, WalletTxType type,
                      BigDecimal availableDelta, BigDecimal frozenDelta,
                      Long refId, Integer escrowRound, String remark) {
        move(wallet, type, availableDelta, frozenDelta, REF_TICKET, refId, escrowRound, remark);
    }

    private void move(UserWallet wallet, WalletTxType type,
                      BigDecimal availableDelta, BigDecimal frozenDelta,
                      String refType, Long refId, Integer escrowRound, String remark) {
        wallet.setAvailableAmount(wallet.getAvailableAmount().add(availableDelta));
        wallet.setFrozenAmount(wallet.getFrozenAmount().add(frozenDelta));

        // 双保险：数据库有 CHECK，这里先在内存里挡一次，报错信息更可读
        if (wallet.getAvailableAmount().signum() < 0) {
            throw new BusinessException(500, "可用余额不能为负");
        }
        if (wallet.getFrozenAmount().signum() < 0) {
            throw new BusinessException(500, "冻结金额不能为负");
        }
        walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(wallet.getUserId());
        tx.setType(type);
        tx.setAmount(availableDelta);
        tx.setFrozenDelta(frozenDelta);
        tx.setBalanceAfter(wallet.getAvailableAmount());
        tx.setRefType(refType);
        tx.setRefId(refId);
        // 轮次对所有 TICKET 流水都填真实值（含改预算）—— 结算前的交叉校验要按轮次求和，
        // 缺了调额那几行就会误判"资金记录不一致"。
        tx.setEscrowRound(escrowRound);
        // 幂等键：只有"可能重复执行"的动作才有值。
        // ESCROW_ADJUST 同一轮可以有很多条，留 null 让唯一索引放行。
        tx.setUniqKey(type == WalletTxType.ESCROW_ADJUST
                ? null
                : refType + ":" + refId + ":" + escrowRound + ":" + type);
        tx.setRemark(remark);
        txRepository.save(tx);
    }

    /** 读钱包，没有就插一行（并发下两个请求同时插会撞主键，撞了就再读） */
    private UserWallet walletOrCreate(Long userId) {
        return walletRepository.findById(userId).orElseGet(() -> {
            try {
                return walletRepository.saveAndFlush(UserWallet.of(userId));
            } catch (DataIntegrityViolationException e) {
                // 另一个请求刚插进来，直接读它那一行
                return walletRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException(500, "钱包初始化失败，请重试"));
            }
        });
    }

    /** 取钱包并加悲观写锁；行不存在时先建再锁 */
    private UserWallet walletForUpdate(Long userId) {
        var locked = walletRepository.findByUserIdForUpdate(userId);
        if (locked.isPresent()) {
            return locked.get();
        }
        walletOrCreate(userId);
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(500, "钱包初始化失败，请重试"));
    }
}
