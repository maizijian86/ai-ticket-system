package com.aiticket.ticket.service;

import com.aiticket.ticket.config.AlipayProperties;
import com.aiticket.ticket.dto.MembershipOrderDTO;
import com.aiticket.ticket.entity.MembershipOrder;
import com.aiticket.ticket.enums.MembershipOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付渠道的编排：处理支付宝回调、以及在读路径上做"查单兜底 + 惰性关闭"。
 *
 * <p><b>本类的方法一概不带 {@code @Transactional}</b>，这是刻意的。它们要调支付宝的网络
 * 接口（可能几秒到几十秒），把它圈进数据库事务会长时间占住 Hikari 连接（池默认只有 10 条），
 * 几笔并发就把整个服务拖垮 —— 与 {@code AiQuotaGate} 里"调 AI 不能落在事务里"是同一个理由。
 * 真正的落库动作交给 {@link MembershipService} 上那些短事务方法，通过代理调用（跨 bean），
 * 事务正常生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipPaymentService {

    /**
     * PENDING 单超过这个时长，才值得去支付宝查一次。
     *
     * <p><b>这只是"什么时候去查"的触发阈值，不是关闭依据。</b>关闭与否由查单结果决定
     * （支付宝说交易已关闭，或压根查不到这笔交易）。写成"超过 15 分钟就关"会直接制造
     * "钱收了、次数不发"的场景 —— 我们关单的那一刻支付宝那边的交易往往还活着。
     *
     * <p>取 15 分钟是因为：用户从点开通到在收银台付完款，正常不会超过这个数；
     * 而支付宝那边的交易超时（默认远长于此）还没到，所以此时去查一定有个明确答案。
     */
    private static final Duration QUERY_AFTER = Duration.ofMinutes(15);

    /** 给支付宝看的成功应答。支付宝只认响应体里出现 "success" */
    private static final String NOTIFY_SUCCESS = "success";
    private static final String NOTIFY_FAILURE = "failure";

    private final MembershipService membershipService;
    private final AlipayGateway alipayGateway;
    private final AlipayProperties alipayProperties;

    // ==================== 异步回调 ====================

    /**
     * 处理支付宝的异步通知，返回值直接作为响应体。
     *
     * <p>返回值的分寸很容易搞反，这里钉死：
     * <ul>
     *   <li>验签失败 / app_id 不对 / 订单查不到 / 金额不符 → 回 <b>failure</b>，
     *       让支付宝重试（很可能只是我们这边暂时故障）；</li>
     *   <li>验签通过但交易还是"未付款"（WAIT_BUYER_PAY 等）→ 回 <b>success</b>：
     *       我收到了、别再重试，这不是错误；</li>
     *   <li>处理成功 → success。</li>
     * </ul>
     * 返回体**不能包 Result**：支付宝只认 body 里出现 {@code success}，回 JSON 会让它
     * 认为通知失败并持续重试（最多 8 次）。
     *
     * <p>另外这个方法**不依赖任何用户身份**（网关白名单放行、并会删掉伪造的 X-User-Id）：
     * 订单是靠 out_trade_no 找出来的，回调里也不需要用户身份。
     */
    public String handleAlipayNotify(Map<String, String> params) {
        if (!alipayGateway.verifyNotify(params)) {
            // 验签失败最常见的两个原因：把「应用公钥」当成了「支付宝公钥」填进配置，
            // 或者网关/反代改动了参数。日志里能看到的只有这一条，没有更多线索。
            log.error("支付宝回调验签失败，已拒绝。outTradeNo={}, tradeStatus={}",
                    params.get("out_trade_no"), params.get("trade_status"));
            return NOTIFY_FAILURE;
        }

        String appId = params.get("app_id");
        if (!alipayProperties.getAppId().equals(appId)) {
            // 防止别的应用（同一个支付宝公钥体系下的）通知打进来
            log.error("支付宝回调的 app_id 与本应用不符，已拒绝。收到={}, 期望={}",
                    appId, alipayProperties.getAppId());
            return NOTIFY_FAILURE;
        }

        String tradeStatus = params.get("trade_status");
        if (!AlipayGateway.TRADE_SUCCESS.equals(tradeStatus)
                && !AlipayGateway.TRADE_FINISHED.equals(tradeStatus)) {
            // 交易已创建但还没付款 —— 收到了、别再重试，这不是错误
            log.info("支付宝回调：交易尚未成功，忽略。outTradeNo={}, tradeStatus={}",
                    params.get("out_trade_no"), tradeStatus);
            return NOTIFY_SUCCESS;
        }

        String outTradeNo = params.get("out_trade_no");
        MembershipOrder order;
        try {
            order = membershipService.loadOrderOr404(outTradeNo);
        } catch (RuntimeException e) {
            // 订单查不到 → 让支付宝重试（可能只是主从延迟或我们暂时故障）
            log.error("支付宝回调找不到订单: outTradeNo={}, tradeNo={}", outTradeNo, params.get("trade_no"));
            return NOTIFY_FAILURE;
        }

        if (!amountMatches(params.get("total_amount"), order)) {
            log.error("支付宝回调金额与订单不符，已拒绝。outTradeNo={}, 回调={}, 订单={}",
                    outTradeNo, params.get("total_amount"), order.getAmount().toPlainString());
            return NOTIFY_FAILURE;
        }

        // 走到这里就该照发。守卫只挡"已到账"、**不挡 CLOSED** —— 见 fulfill 的注释：
        // 我们主动关掉的单，支付宝那边可能还活着并被用户付掉。
        membershipService.fulfill(outTradeNo, params.get("trade_no"));
        return NOTIFY_SUCCESS;
    }

    /**
     * 金额比对。
     *
     * <p>必须用 {@code BigDecimal.compareTo}，**不能**用 equals 或字符串比：
     * 30 元在支付宝那边回传的是 {@code "30.00"}，我们库里也是 30.00，
     * 但一旦标度不同（{@code "30"} vs {@code "30.00"}），字符串比必然为 false ——
     * 那时的现象是"钱付了、回调一直返回 failure、次数永远不发"。
     *
     * <p>回调缺 total_amount 或它不是合法数字，一律按不符处理（宁可让支付宝重试，
     * 也不能凭一个解析不出来的金额发货）。
     */
    private static boolean amountMatches(String notifiedAmount, MembershipOrder order) {
        if (notifiedAmount == null) {
            return false;
        }
        try {
            return MembershipService.sameAmount(new BigDecimal(notifiedAmount), order.getAmount());
        } catch (NumberFormatException e) {
            log.error("支付宝回调的 total_amount 不是合法金额: {}", notifiedAmount);
            return false;
        }
    }

    // ==================== 读路径上的查单兜底 ====================

    /**
     * 单笔订单状态（结果页轮询用）。**带侧效应**：PENDING 且已超过阈值时主动查一次支付宝。
     *
     * <p>GET 带写侧效应不优雅，换来的是"不需要定时任务、也不需要前端多调一个 POST"。
     * 全仓没有任何 {@code @EnableScheduling}，而且正确性不该取决于某个 job 恰好跑过。
     * 更干净的做法是另开 {@code POST /orders/{no}/sync}，本次不拆。
     *
     * <p>为什么必须做这件事（不是锦上添花）：natapp 免费隧道域名重启后变了、
     * 用户付完直接关浏览器（同步跳转压根不发生）、穿透掉线的窗口期支付宝重试几次就放弃 ——
     * 没有这条兜底，这些情况都会变成"钱付了、次数没到"且**没有任何自愈路径**。
     * 这也解释了为什么不能只信 return_url：那个跳转只说明"浏览器回来了"，
     * 不说明"钱到账了"，任何人手工构造那个 URL 都能走到。
     */
    public MembershipOrderDTO getOrderWithSync(Long userId, String outTradeNo) {
        membershipService.requireLogin(userId);
        MembershipOrder order = membershipService.loadOrderOr404(outTradeNo);
        membershipService.requireOwnership(order, userId);

        if (shouldQueryAlipay(order)) {
            syncFromAlipay(outTradeNo);
        }
        return membershipService.reload(outTradeNo);
    }

    private static boolean shouldQueryAlipay(MembershipOrder order) {
        return order.getStatus() == MembershipOrderStatus.PENDING
                && order.getCreatedAt() != null
                && order.getCreatedAt().isBefore(LocalDateTime.now().minus(QUERY_AFTER));
    }

    /**
     * 主动查一次支付宝，按查询结果分三条路走。
     *
     * <p><b>判据是支付宝的返回，不是我们的钟</b>：
     * <ul>
     *   <li>交易成功 → 走 {@code fulfill}（兜底的主路径）；</li>
     *   <li>交易已关闭（用户自己取消或支付宝侧超时） → 标 CLOSED；</li>
     *   <li>**查不到这笔交易**（用户压根没提交支付，我们只生成过链接） → 标 CLOSED。</li>
     * </ul>
     * 查单本身失败（网络/权限）时 {@code queryTrade} 会抛异常，这里让它往上抛 ——
     * 绝不能把"查不到结果"和"查不到交易"混为一谈，后者会白白关掉一张可能已付款的单。
     */
    private void syncFromAlipay(String outTradeNo) {
        AlipayGateway.TradeQuery query = alipayGateway.queryTrade(outTradeNo);

        if (query == null) {
            log.info("主动查单：支付宝侧无此交易，关闭订单。outTradeNo={}", outTradeNo);
            membershipService.closeIfPending(outTradeNo);
            return;
        }
        if (query.isPaid()) {
            // 查单结果里的金额也核一遍：能到这一步说明它和回调是同一笔交易，
            // 但金额不符时宁可人工介入，也不要凭一个可疑的金额发货
            MembershipOrder order = membershipService.loadOrderOr404(outTradeNo);
            if (!amountMatches(query.totalAmount(), order)) {
                log.error("主动查单发现金额不符，未发放。outTradeNo={}, 查单={}, 订单={}",
                        outTradeNo, query.totalAmount(), order.getAmount().toPlainString());
                return;
            }
            log.info("主动查单确认已支付，补发会员权益: outTradeNo={}", outTradeNo);
            membershipService.fulfill(outTradeNo, query.tradeNo());
            return;
        }
        if (query.isClosed()) {
            log.info("主动查单：支付宝侧交易已关闭，关闭订单。outTradeNo={}", outTradeNo);
            membershipService.closeIfPending(outTradeNo);
            return;
        }

        // 其它状态（如 WAIT_BUYER_PAY）：用户还在收银台上，什么都不要动
        log.info("主动查单：交易仍在进行中，保持待支付。outTradeNo={}, tradeStatus={}",
                outTradeNo, query.tradeStatus());
    }
}
