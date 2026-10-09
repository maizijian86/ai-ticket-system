package com.aiticket.ticket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝沙箱配置，绑定 application.yml 的 {@code alipay:} 段。
 *
 * <p>真值（app-id / 私钥 / 支付宝公钥 / 隧道域名）来自
 * {@code application-secret.yml}（已在 .gitignore 中排除，由 application.yml 的
 * {@code spring.config.import} 自动加载），application.yml 里只写
 * {@code ${ALIPAY_APP_ID:}} 这样的占位引用。密钥文件缺失时各字段为空串，
 * 服务照常启动，只有真正发起支付时才会失败 —— 不因为少了本地密钥就起不来。
 *
 * <p><b>notify-url 必须可配置</b>：natapp 免费隧道的域名每次重启都会变，
 * 写死等于回调静默丢失，而现象是"付了钱没到账、日志里什么都没有"。
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 沙箱应用的 APPID */
    private String appId = "";

    /** 应用私钥（PKCS8，一行到底不带换行，不含 BEGIN/END 头尾） */
    private String privateKey = "";

    /** **沙箱控制台给出的**「支付宝公钥」，不是本地生成的应用公钥（两者不是一回事） */
    private String publicKey = "";

    /** 网关地址。新版沙箱是 openapi-sandbox.dl.alipaydev.com，老版没有 -sandbox 段 */
    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    private String signType = "RSA2";

    private String charset = "UTF-8";

    /** 异步通知地址，即 natapp 隧道域名 + /api/v1/membership/callback/alipay */
    private String notifyUrl = "";

    /** 付款成功后浏览器跳回的地址（我们的前端结果页） */
    private String returnUrl = "http://localhost:3000/membership/result";

    /**
     * 关键字段是否齐备。
     *
     * <p>不是启动期校验：没配密钥时服务该能正常跑（其余功能不受影响），
     * 只在用户真的点「立即开通」时给出人能看懂的错误（见 MembershipService.createOrder）。
     */
    public boolean isConfigured() {
        return !appId.isBlank() && !privateKey.isBlank() && !publicKey.isBlank()
                && !gatewayUrl.isBlank() && !notifyUrl.isBlank();
    }

    /** 缺了哪些字段，拼成一句话给日志/报错用（不打印密钥内容本身） */
    public String missingFields() {
        StringBuilder sb = new StringBuilder();
        if (appId.isBlank()) sb.append("app-id ");
        if (privateKey.isBlank()) sb.append("private-key ");
        if (publicKey.isBlank()) sb.append("public-key ");
        if (gatewayUrl.isBlank()) sb.append("gateway-url ");
        if (notifyUrl.isBlank()) sb.append("notify-url ");
        return sb.toString().trim();
    }
}
