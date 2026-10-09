package com.aiticket.ticket.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配支付宝客户端 bean。
 *
 * <p>全局只有一个 {@link AlipayClient}：它内部持有连接池与签名器，每处 {@code new}
 * 一个既浪费又会让超时/重试配置分散在多处漂移。参考实现是把参数硬编码在工具类里
 * 每次现拼（PayUtil.java），这里不学。
 *
 * <p><b>刻意不做"密钥缺失就启动失败"的校验。</b>没配密钥时这个 bean 照样建出来，
 * 等到真的发起支付再由 {@code MembershipService.createOrder} 给出人能看懂的错误 ——
 * 本地没配支付宝密钥不应该让整个 ticket-service 起不来。
 */
@Slf4j
@Configuration
public class AlipayClientConfig {

    @Bean
    public AlipayClient alipayClient(AlipayProperties properties) throws Exception {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.getGatewayUrl());
        config.setAppId(properties.getAppId());
        // 应用私钥（PKCS8、一行到底、不含 BEGIN/END 头尾）
        config.setPrivateKey(properties.getPrivateKey());
        // 沙箱控制台给出的「支付宝公钥」—— 用来**验**支付宝发回来的签名。
        // 注意这跟上面那把私钥不是一对里的公钥（见 会员额度plan.txt 七.7）
        config.setAlipayPublicKey(properties.getPublicKey());
        config.setFormat("json");
        config.setCharset(properties.getCharset());
        config.setSignType(properties.getSignType());

        log.info("支付宝沙箱客户端已装配: appId={}, gateway={}, notifyUrl={}, 密钥{}",
                mask(properties.getAppId()), properties.getGatewayUrl(), properties.getNotifyUrl(),
                properties.isConfigured() ? "已配置" : "缺失(" + properties.missingFields() + ")，支付会不可用");
        return new DefaultAlipayClient(config);
    }

    /** 日志里不打印完整 APPID；密钥本身一个字符都不打 */
    private static String mask(String appId) {
        if (appId == null || appId.length() <= 6) {
            return appId;
        }
        return appId.substring(0, 4) + "****" + appId.substring(appId.length() - 4);
    }
}
