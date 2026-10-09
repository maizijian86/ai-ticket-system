package com.aiticket.ticket.controller;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.ticket.dto.CreateOrderRequest;
import com.aiticket.ticket.dto.MembershipDTO;
import com.aiticket.ticket.dto.MembershipOrderDTO;
import com.aiticket.ticket.dto.MembershipPayDTO;
import com.aiticket.ticket.dto.MembershipPlanDTO;
import com.aiticket.ticket.service.MembershipPaymentService;
import com.aiticket.ticket.service.MembershipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员与会员订单。身份一律从网关注入的 {@code X-User-Id} 头取（与全站一致），
 * **不自己解析 Authorization 头**。
 *
 * <p>六个接口里只有回调那个是例外：它由支付宝服务器直接 POST 过来，没有 token，
 * 走网关白名单放行（见 JwtAuthenticationFilter 的 WHITE_LIST），返回的也不是
 * {@code Result} 而是纯文本 —— 详见 {@link #alipayCallback}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;
    private final MembershipPaymentService paymentService;

    /** 套餐列表。价格与权益文案的唯一来源（前端不写死） */
    @GetMapping("/plans")
    public Result<List<MembershipPlanDTO>> plans() {
        return Result.success(membershipService.listPlans());
    }

    /** 我的会员状态 + 账户余额 */
    @GetMapping("/me")
    public Result<MembershipDTO> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(membershipService.me(userId));
    }

    /** 创建订单，返回支付宝收银台链接。链接由后端签发 —— 前端不碰私钥 */
    @PostMapping("/orders")
    public Result<MembershipPayDTO> createOrder(@Valid @RequestBody CreateOrderRequest request,
                                                @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(membershipService.createOrder(userId, request.getPlanCode()));
    }

    /**
     * 我的订单（分页）。**只读我们库**，不查支付宝 —— 列表页分页查一次要打 N 次外部接口。
     *
     * <p>条件 {@code user_id = 当前用户} 在服务层强制，不接受前端传 userId。
     */
    @GetMapping("/orders")
    public Result<PageResult<MembershipOrderDTO>> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(membershipService.listOrders(userId, page, pageSize));
    }

    /**
     * 单笔订单状态，结果页轮询用。PENDING 且超过阈值时会主动查一次支付宝兜底。
     *
     * <p>路径变量与上面那条 {@code /orders} 不冲突：Spring 按段匹配，{@code /orders} 与
     * {@code /orders/{outTradeNo}} 是两个不同的模式。
     */
    @GetMapping("/orders/{outTradeNo}")
    public Result<MembershipOrderDTO> getOrder(@PathVariable String outTradeNo,
                                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(paymentService.getOrderWithSync(userId, outTradeNo));
    }

    /**
     * 支付宝异步通知。**不鉴权**（网关白名单放行），也**不能**返回 {@code Result}。
     *
     * <p>支付宝只认响应体里出现 {@code success}；回一段 JSON（哪怕是 200）它都认为
     * 通知失败，然后按 4m/10m/10m/1h/2h/6h/15h 的节奏重试最多 8 次。参考实现里
     * {@code AlipayController} 那套 {@code Result.success(...)} 在这里是错的。
     *
     * <p>参数直接从 {@code getParameterMap()} 取，**不写 DTO**：支付宝的参数名带下划线、
     * 各接口还不完全一样，用 DTO 接容易漏字段，而漏一个字段验签必然失败。
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
            log.error("处理支付宝回调时发生异常: outTradeNo={}", params.get("out_trade_no"), e);
            return "failure";
        }
    }
}
