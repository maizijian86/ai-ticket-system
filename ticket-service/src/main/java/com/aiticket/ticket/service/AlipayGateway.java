package com.aiticket.ticket.service;

import com.aiticket.common.exception.BusinessException;
import com.aiticket.ticket.config.AlipayProperties;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.internal.util.json.JSONWriter;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝沙箱的唯一出口 —— **全仓只有这个类碰 SDK**。
 *
 * <p>隔离在这里的原因很实际：SDK 的异常类型、返回码约定、bizContent 的拼法都是外部细节，
 * 散进业务代码之后，改渠道（或者哪天接第二个渠道）要在整个 service 层里找。
 * 这里也只做"发请求、验签名"，不做任何业务判断（比如"这笔钱算不算数"）——
 * 那些属于 {@link MembershipService}。
 *
 * <p>本次**不含退款**：会员是虚拟权益，一开通次数就发出去了，没有"退钱"这个动作。
 * 所以这个类里一行 {@code AlipayTradeRefund} 相关的东西都没有（连 import 都不写），
 * 参考实现里的退款代码整块没有移植。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayGateway {

    // ==================== 支付宝的交易状态 ====================
    /** 交易创建、等待买家付款。**不是错误**，回调收到它应当回 success 让支付宝别再重试 */
    public static final String TRADE_WAIT_BUYER_PAY = "WAIT_BUYER_PAY";
    /** 交易支付成功 —— 到账的唯一主路径 */
    public static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    /** 交易结束、不可退款。与 TRADE_SUCCESS 一样按"已付款"处理 */
    public static final String TRADE_FINISHED = "TRADE_FINISHED";
    /** 交易关闭（用户自己取消，或支付宝侧超时关闭） */
    public static final String TRADE_CLOSED = "TRADE_CLOSED";
    /** 查单时"这笔交易不存在"的子错误码：用户压根没提交支付，我们只生成过链接 */
    private static final String SUB_CODE_TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    /** PC 网页支付的产品码，AlipayTradePagePay 固定用它 */
    private static final String PRODUCT_CODE_PAGE_PAY = "FAST_INSTANT_TRADE_PAY";

    /** 发 bizContent 用。SDK 自带的 JSON 写法，省得手工拼字符串把中文 subject 拼坏 */
    private static final JSONWriter JSON = new JSONWriter();

    /**
     * 把参数拼成 bizContent。
     *
     * <p>喂给 SDK 的 {@code JSONWriter} 的是一个 **{@code Map}**，而不是 SDK 自带的那几个
     * {@code AlipayTradeXxxModel} —— 这不是风格选择，是个实打实的坑：那些 model 的字段上
     * 挂着 {@code @ApiField("out_trade_no")}，但 {@code JSONWriter} **不认这个注解**，
     * 它会照着 Java 属性名序列化成 {@code {"outTradeNo":…,"totalAmount":…}}。
     * 那样的 bizContent 发给支付宝等于什么都没传（参数名对不上，会被当未知字段忽略），
     * 现象是收银台打开后金额为空/报参数错误，而本地看起来一切正常。
     *
     * <p>而 {@code Map} 的键是**原样输出**的，于是蛇形命名写在这里一眼可见、可核对。
     * 转义（中文 subject 里的括号等）仍交给 SDK，不自己拼字符串。
     */
    private static String bizContent(Map<String, String> fields) {
        return JSON.write(fields);
    }

    private final AlipayClient alipayClient;
    private final AlipayProperties properties;

    /**
     * 生成 PC 网页支付的收银台链接。
     *
     * <p>走的是 {@code pageExecute(request, "GET")} —— 它返回的是**带签名的 URL 字符串**，
     * 前端直接跳过去即可，少一层 HTML 传递。若某个版本返回的是自动提交的 form HTML，
     * 这个字符串就以 {@code <form} 开头，前端两种都能处理（见 MembershipResult 的跳转逻辑）。
     *
     * <p>这里返回的字符串里带着**下单那一刻**的 notify-url，所以改了 natapp 域名之后，
     * 旧的待支付订单不要再付 —— 重新下单（或者由下单接口重新生成链接）。
     *
     * @param subject 商品标题，会显示在收银台上
     */
    public String buildPagePayUrl(String outTradeNo, BigDecimal amount, String subject) {
        return buildPagePayUrl(outTradeNo, amount, subject,
                properties.getNotifyUrl(), properties.getReturnUrl());
    }

    /**
     * 同上，但**显式指定回调与跳回地址** —— 钱包充值走这条路：
     * 充值的异步通知必须落到 {@code /api/v1/wallet/callback/alipay}，
     * 不能复用会员那个地址（两个处理器只认自己的单号前缀）。
     *
     * @param notifyUrl 异步通知地址（支付宝服务器直接 POST 到这里）
     * @param returnUrl 付款成功后浏览器跳回的地址
     */
    public String buildPagePayUrl(String outTradeNo, BigDecimal amount, String subject,
                                  String notifyUrl, String returnUrl) {
        Map<String, String> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", outTradeNo);
        // 金额必须两位小数的字符串："30" 与 "30.00" 在支付宝那边不是一回事。
        // 我们内部金额是**整数元**（BigDecimal 100），所以这里统一补成 "100.00"。
        // setScale 用 UNNECESSARY：万一哪天有人塞进来三位小数，直接抛错而不是静默进位到
        // 一个和订单金额不同的数字（那会导致回调的金额比对失败、钱付了余额不加）。
        biz.put("total_amount", amount.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
        biz.put("subject", subject);
        biz.put("product_code", PRODUCT_CODE_PAGE_PAY);

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);
        request.setBizContent(bizContent(biz));

        try {
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request, "GET");
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                throw new BusinessException(502, "支付宝没有返回收银台地址" + detailOf(response));
            }
            log.info("已生成支付宝收银台链接: outTradeNo={}, amount={}", outTradeNo, amount.toPlainString());
            return body;
        } catch (AlipayApiException e) {
            log.error("生成支付宝收银台链接失败: outTradeNo={}", outTradeNo, e);
            throw new BusinessException(502, "生成支付链接失败：" + e.getMessage());
        }
    }

    /**
     * 主动查单 —— 结果页轮询时用的兜底，也是"订单该不该关闭"的唯一判据。
     *
     * <p>这一步不是锦上添花：natapp 免费隧道域名重启后会变、用户付完直接关浏览器
     * （同步跳转压根不发生）、穿透掉线的窗口期支付宝重试几次就放弃 —— 没有主动查单，
     * 这些情况都会变成"钱付了、次数没到"，而且没有任何自愈路径，只能人工改库。
     *
     * @return 查到就返回交易信息；**返回 null 表示支付宝那边查不到这笔交易**
     *         （用户压根没提交支付，我们只生成过链接）—— 这是"可以关闭订单"的信号之一，
     *         与"查单失败"必须区分开，后者要抛异常而不是当成不存在
     * @throws BusinessException 502 查单本身失败（网络/权限/系统错误）——
     *         此时**绝不能**据此关闭订单，那会把钱挡在门外
     */
    public TradeQuery queryTrade(String outTradeNo) {
        Map<String, String> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", outTradeNo);

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent(bizContent(biz));

        AlipayTradeQueryResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            log.error("查询支付宝交易失败: outTradeNo={}", outTradeNo, e);
            throw new BusinessException(502, "查询支付结果失败，请稍后重试");
        }

        if (response.isSuccess()) {
            log.info("查询支付宝交易: outTradeNo={}, tradeStatus={}, tradeNo={}",
                    outTradeNo, response.getTradeStatus(), response.getTradeNo());
            return new TradeQuery(response.getTradeStatus(), response.getTradeNo(), response.getTotalAmount());
        }

        if (SUB_CODE_TRADE_NOT_EXIST.equals(response.getSubCode())) {
            // 交易不存在 ≠ 查单失败：这是"用户从没付过"的确定结论，是关闭订单的依据
            log.info("支付宝侧无此交易: outTradeNo={}", outTradeNo);
            return null;
        }

        log.error("查询支付宝交易返回失败: outTradeNo={}, code={}, subCode={}, subMsg={}",
                outTradeNo, response.getCode(), response.getSubCode(), response.getSubMsg());
        throw new BusinessException(502, "查询支付结果失败：" + response.getSubMsg());
    }

    /**
     * 回调验签。
     *
     * <p>验的是"这段参数确实是支付宝用它的私钥签的"。用 {@code properties.getPublicKey()}
     * （沙箱控制台给出的**支付宝公钥**）而不是本地那把应用公钥 —— 填错的表现是
     * 回调永远验签失败、报 invalid-signature，而且错误信息不会告诉你是哪个公钥错了。
     *
     * <p>验签通过**只是**"这消息可信"，不代表"这笔钱该发次数" —— 金额与单号还要再校验
     * （见 {@link MembershipService#handleAlipayNotify}）。
     */
    public boolean verifyNotify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(params, properties.getPublicKey(),
                    properties.getCharset(), properties.getSignType());
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常: {}", e.getMessage());
            return false;
        }
    }

    /** 主动查单的结果。字段名对齐支付宝的响应，不做业务加工 */
    public record TradeQuery(String tradeStatus, String tradeNo, String totalAmount) {

        /** 这笔交易支付宝确认收到钱了（TRADE_SUCCESS / TRADE_FINISHED 都算） */
        public boolean isPaid() {
            return TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus);
        }

        /** 支付宝侧确认这笔交易已经关闭，成交无望 */
        public boolean isClosed() {
            return TRADE_CLOSED.equals(tradeStatus);
        }
    }

    /** 把 SDK 响应的错误信息摘成一句话（不含密钥，可安全写日志/返回给前端） */
    private static String detailOf(AlipayResponse response) {
        if (response == null) {
            return "（无响应）";
        }
        return "（code=" + response.getCode() + ", subCode=" + response.getSubCode()
                + ", subMsg=" + response.getSubMsg() + "）";
    }
}
