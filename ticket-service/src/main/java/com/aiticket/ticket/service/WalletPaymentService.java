package com.aiticket.ticket.service;

import com.aiticket.ticket.config.AlipayProperties;
import com.aiticket.ticket.dto.WalletOrderDTO;
import com.aiticket.ticket.entity.WalletOrder;
import com.aiticket.ticket.enums.WalletOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 充值支付渠道的编排：处理支付宝回调、以及在读路径上做"查单兜底 + 惰性关闭"。
 *
 * <p>与 {@code MembershipPaymentService} 是同一套骨架，刻意**复制而不是抽象**：
 * 两边的金额校验规则不同（充值是用户自填、会员是套餐定价），
 * 而且线上出问题时"两个业务共用一个支付编排类"会让排查变成猜。
 *
 * <p><b>本类的方法一概不带 {@code @Transactional}</b>，这是刻意的。它们要调支付宝的
 * 网络接口（可能几秒到几十秒），把它圈进数据库事务会长时间占住 Hikari 连接
 *（池默认只有 10 条），几笔并发就把整个服务拖垮 —— 与 {@code AiQuotaGate} 里
 * "调 AI 不能落在事务里"是同一个理由。真正的落库动作交给 {@link WalletService}
 * 上那些短事务方法，通过代理调用（跨 bean），事务正常生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletPaymentService {

    /**
     * PENDING 单超过这个时长，才值得去支付宝查一次。
     *
     * <p><b>这只是"什么时候去查"的触发阈值，不是关闭依据。</b>关闭与否由查单结果决定
     *（支付宝说交易已关闭，或压根查不到这笔交易）。写成"超过 15 分钟就关"会直接制造
     * "钱收了、余额不加"的场景 —— 我们关单的那一刻支付宝那边的交易往往还活着。
     */
    private static final Duration QUERY_AFTER = Duration.ofMinutes(15);

    /** 给支付宝看的成功应答。支付宝只认响应体里出现 "success" */
    private static final String NOTIFY_SUCCESS = "success";
    private static final String NOTIFY_FAILURE = "failure";

    private final WalletService walletService;
    private final AlipayGateway alipayGateway;
    private final AlipayProperties alipayProperties;

    // ==================== 异步回调 ====================

    /**
     * 处理支付宝的异步通知，返回值直接作为响应体。
     *
     * <p>返回值分寸（与会员那条一致，别改）：
     * <ul>
     *   <li>验签失败 / app_id 不对 / 订单查不到 / 金额不符 → <b>failure</b>，让支付宝重试；</li>
     *   <li>验签通过但交易还没付款（WAIT_BUYER_PAY 等）→ <b>success</b>（收到了、别再重试）；</li>
     *   <li>处理成功 → success。</li>
     * </ul>
     * 返回体**不能包 Result**：支付宝只认 body 里出现 {@code success}，回 JSON 会让它
     * 认为通知失败并持续重试（最多 8 次）。
     *
     * <p>本方法**不依赖任何用户身份**（网关白名单放行、并会删掉伪造的 X-User-Id）：
     * 订单是靠 out_trade_no 找出来的。
     */
    public String handleAlipayNotify(Map<String, String> params) {
        // 原始参数整条留档：验签失败时这是唯一能复现的线索；重放这笔回调必须原样带全部参数。
        // 故意不遮蔽 buyer_* 之类的字段：遮蔽了签名就重算不出来，留档也失去意义。
        log.info("支付宝充值回调原始参数: {}", new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")));

        if (!alipayGateway.verifyNotify(params)) {
            log.error("支付宝充值回调验签失败，已拒绝。outTradeNo={}, tradeStatus={}",
                    params.get("out_trade_no"), params.get("trade_status"));
            return NOTIFY_FAILURE;
        }

        String appId = params.get("app_id");
        if (!alipayProperties.getAppId().equals(appId)) {
            // 防止别的应用（同一个支付宝公钥体系下的）通知打进来
            log.error("支付宝充值回调的 app_id 与本应用不符，已拒绝。收到={}, 期望={}",
                    appId, alipayProperties.getAppId());
            return NOTIFY_FAILURE;
        }

        String tradeStatus = params.get("trade_status");
        if (!AlipayGateway.TRADE_SUCCESS.equals(tradeStatus)
                && !AlipayGateway.TRADE_FINISHED.equals(tradeStatus)) {
            log.info("支付宝充值回调：交易尚未成功，忽略。outTradeNo={}, tradeStatus={}",
                    params.get("out_trade_no"), tradeStatus);
            return NOTIFY_SUCCESS;
        }

        String outTradeNo = params.get("out_trade_no");
        WalletOrder order;
        try {
            order = walletService.loadOrderOr404(outTradeNo);
        } catch (RuntimeException e) {
            // 订单查不到 → 让支付宝重试（可能只是主从延迟）
            log.error("支付宝充值回调找不到订单: outTradeNo={}, tradeNo={}",
                    outTradeNo, params.get("trade_no"));
            return NOTIFY_FAILURE;
        }

        if (!amountMatches(params.get("total_amount"), order)) {
            log.error("支付宝充值回调金额与订单不符，已拒绝。outTradeNo={}, 回调={}, 订单={}",
                    outTradeNo, params.get("total_amount"), order.getAmount().toPlainString());
            return NOTIFY_FAILURE;
        }

        // 走到这里就该入账。守卫只挡"已到账"、**不挡 CLOSED** —— 见 fulfillRecharge 的注释：
        // 我们主动关掉的单，支付宝那边可能还活着并被用户付掉。
        walletService.fulfillRecharge(outTradeNo, params.get("trade_no"));
        return NOTIFY_SUCCESS;
    }

    /**
     * 金额比对。
     *
     * <p>必须用 {@code BigDecimal.compareTo}，**不能**用 equals 或字符串比：
     * 100 元在支付宝那边回传的是 {@code "100.00"}，我们库里是 100.00，
     * 一旦标度不同（{@code "100"} vs {@code "100.00"}），字符串比必然为 false ——
     * 那时的现象是"钱付了、回调一直返回 failure、余额永远不加"。
     *
     * <p>缺 total_amount 或它不是合法数字，一律按不符处理（宁可让支付宝重试，
     * 也不能凭一个解析不出来的金额入账）。
     */
    private static boolean amountMatches(String notifiedAmount, WalletOrder order) {
        if (notifiedAmount == null) {
            return false;
        }
        try {
            return new BigDecimal(notifiedAmount).compareTo(order.getAmount()) == 0;
        } catch (NumberFormatException e) {
            log.error("支付宝充值回调的 total_amount 不是合法金额: {}", notifiedAmount);
            return false;
        }
    }

    // ==================== 读路径上的查单兜底 ====================

    /**
     * 单笔充值单状态（结果页轮询用）。**带侧效应**：PENDING 且已超过阈值时主动查一次支付宝。
     *
     * <p>为什么必须做这件事（不是锦上添花）：natapp 免费隧道域名重启后变了、
     * 用户付完直接关浏览器（同步跳转压根不发生）、穿透掉线的窗口期支付宝重试几次就放弃 ——
     * 没有这条兜底，这些情况都会变成"钱付了、余额没加"且**没有任何自愈路径**。
     *
     * <p>也不能只信 return_url：那个跳转只说明"浏览器回来了"，不说明"钱到账了"，
     * 任何人手工构造那个 URL 都能走到。
     */
    public WalletOrderDTO getOrderWithSync(Long userId, String outTradeNo, boolean force) {
        WalletOrder order = walletService.loadOrderOr404(outTradeNo);
        walletService.requireOwnership(order, userId);

        if (shouldQueryAlipay(order, force)) {
            syncFromAlipay(outTradeNo);
        }
        return walletService.orderDto(outTradeNo);
    }

    /**
     * 该不该现在向支付宝查一次。
     *
     * <p>三个条件缺一不可：仍待支付、有创建时间、以及满足下面之一 ——
     *   · {@code force=true}：结果页**首次进入**与用户点「刷新状态」时传。
     *     那两个时刻用户刚离开收银台，正是最该核对的时刻；原先一律等 15 分钟，
     *     用户会盯着「等待支付确认」干等，明明钱已经付了。
     *   · 创建超过 {@code QUERY_AFTER}（15 分钟）：轮询兜底，防止回调彻底丢失。
     *
     * <p>轮询走 {@code force=false}，避免每 2 秒打一次支付宝。
     * 非 PENDING（已支付 / 已关闭）一律不查 —— 终态没必要再问。
     */
    private static boolean shouldQueryAlipay(WalletOrder order, boolean force) {
        return order.getStatus() == WalletOrderStatus.PENDING
                && order.getCreatedAt() != null
                && (force || order.getCreatedAt().isBefore(LocalDateTime.now().minus(QUERY_AFTER)));
    }

    /**
     * 主动查一次支付宝，按查询结果分三条路走。
     *
     * <p><b>判据是支付宝的返回，不是我们的钟</b>：
     * <ul>
     *   <li>交易成功 → 走 {@code fulfillRecharge}（兜底的主路径）；</li>
     *   <li>交易已关闭 → 标 CLOSED；</li>
     *   <li>**查不到这笔交易**（用户压根没提交支付，我们只生成过链接） → 标 CLOSED。</li>
     * </ul>
     * 查单本身失败（网络/权限）时 {@code queryTrade} 会抛异常，这里让它往上抛 ——
     * 绝不能把"查不到结果"和"查不到交易"混为一谈，后者会白白关掉一张可能已付款的单。
     */
    private void syncFromAlipay(String outTradeNo) {
        AlipayGateway.TradeQuery query = alipayGateway.queryTrade(outTradeNo);

        if (query == null) {
            log.info("主动查单：支付宝查不到这笔充值交易，关闭订单。outTradeNo={}", outTradeNo);
            walletService.closeRechargeIfPending(outTradeNo);
            return;
        }
        if (query.isPaid()) {
            WalletOrder order = walletService.loadOrderOr404(outTradeNo);
            if (!amountMatches(query.totalAmount(), order)) {
                log.error("主动查单发现充值金额不符，未入账。outTradeNo={}, 查单={}, 订单={}",
                        outTradeNo, query.totalAmount(), order.getAmount().toPlainString());
                return;
            }
            log.info("主动查单确认充值已支付，补记余额: outTradeNo={}", outTradeNo);
            walletService.fulfillRecharge(outTradeNo, query.tradeNo());
            return;
        }
        if (query.isClosed()) {
            log.info("主动查单：支付宝侧交易已关闭，关闭充值订单。outTradeNo={}", outTradeNo);
            walletService.closeRechargeIfPending(outTradeNo);
            return;
        }

        // 其它状态（如 WAIT_BUYER_PAY）：用户还在收银台上，什么都不要动
        log.info("主动查单：充值交易仍在进行中，保持待支付。outTradeNo={}, tradeStatus={}",
                outTradeNo, query.tradeStatus());
    }
}
