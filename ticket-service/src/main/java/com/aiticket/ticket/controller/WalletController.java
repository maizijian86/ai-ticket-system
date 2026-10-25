package com.aiticket.ticket.controller;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.ticket.dto.RechargeRequest;
import com.aiticket.ticket.dto.UserWalletDTO;
import com.aiticket.ticket.dto.WalletOrderDTO;
import com.aiticket.ticket.dto.WalletPayDTO;
import com.aiticket.ticket.dto.WalletTxDTO;
import com.aiticket.ticket.service.WalletPaymentService;
import com.aiticket.ticket.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 钱包与充值。身份一律从网关注入的 {@code X-User-Id} 头取（与全站一致），
 * **不自己解析 Authorization 头**。
 *
 * <p>五个接口里只有回调那个是例外：它由支付宝服务器直接 POST 过来，没有 token，
 * 走网关白名单放行（见 JwtAuthenticationFilter 的 WHITE_LIST），返回的也不是
 * {@code Result} 而是纯文本 —— 详见 {@link #alipayCallback}。
 *
 * <p><b>余额不可提现</b>：本模块只做"充值 + 站内使用（发布工单托管）"，
 * 没有提现接口，也不打算有。前端文案必须说清这一点。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WalletPaymentService paymentService;

    /** 我的钱包：可用 / 冻结 / 合计 + 充值上下限 */
    @GetMapping("/me")
    public Result<UserWalletDTO> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(walletService.me(userId));
    }

    /** 我的充值记录（分页）。只读我们库，不查支付宝 */
    @GetMapping("/orders")
    public Result<PageResult<WalletOrderDTO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(walletService.listOrders(userId, page, pageSize));
    }

    /** 我的资金流水（分页）。审计账本，只增不改 */
    @GetMapping("/transactions")
    public Result<PageResult<WalletTxDTO>> listTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(walletService.listTransactions(userId, page, pageSize));
    }
    /** 创建充值订单，返回支付宝收银台链接。链接由后端签发 —— 前端不碰私钥 */
    @PostMapping("/orders")
    public Result<WalletPayDTO> createOrder(@Valid @RequestBody RechargeRequest request,
                                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(walletService.createRechargeOrder(userId, request.getAmount()));
    }

    /**
     * 单笔充值单状态，结果页轮询用。PENDING 且超过阈值时会主动查一次支付宝兜底。
     *
     * <p>与上面的 {@code /orders} 不冲突：Spring 按段匹配，
     * {@code /orders} 与 {@code /orders/{outTradeNo}} 是两个不同的模式。
     */
    @GetMapping("/orders/{outTradeNo}")
    public Result<WalletOrderDTO> getOrder(@PathVariable String outTradeNo,
                                           @RequestParam(defaultValue = "false") boolean force,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(paymentService.getOrderWithSync(userId, outTradeNo, force));
    }

    /**
     * 支付宝异步通知。**不鉴权**（网关白名单放行），也**不能**返回 {@code Result}。
     *
     * <p>支付宝只认响应体里出现 {@code success}；回一段 JSON（哪怕 200）它都认为
     * 通知失败，然后按 4m/10m/10m/1h/2h/6h/15h 的节奏重试最多 8 次。
     *
     * <p>参数直接从 {@code getParameterMap()} 取，**不写 DTO**：支付宝的参数名带下划线、
     * 各接口还不完全一样，用 DTO 接容易漏字段，而漏一个字段验签必然失败。
     *
     * <p>⚠️ 这个路径必须出现在网关白名单里（{@code /api/v1/wallet/callback}），
     * 否则支付宝的回调会被 401 挡在最前面 —— 现象是"付了钱余额没加，日志一片安静"。
     */
    @PostMapping("/callback/alipay")
    public String alipayCallback(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });

        try {
            return paymentService.handleAlipayNotify(params);
        } catch (Exception e) {
            // 绝不能把异常抛给全局处理器：那会返回一段 JSON，支付宝判为失败并重试，
            // 而重试同样会失败 —— 这里明确回 failure，让日志留下真正的原因
            log.error("处理支付宝充值回调时发生异常: outTradeNo={}", params.get("out_trade_no"), e);
            return "failure";
        }
    }
}
