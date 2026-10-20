package com.aiticket.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 的配置项，前缀 {@code aliyun.oss}。**纯配置，不含任何操作** ——
 * 上传/删除/拼地址都在 {@link OssStorage} 里。
 *
 * <p><b>AK 只从环境变量来</b>：application.yml 里写的是 {@code ${OSS_ACCESS_KEY_ID}} 这种
 * 占位，真正的值在机器上（用户已配好 {@code OSS_ACCESS_KEY_ID} / {@code OSS_ACCESS_KEY_SECRET}）。
 * yml 是提交进仓库的，AK 写进去等于泄露 —— 而且是那种"改一次要改历史提交"的泄露。
 *
 * <p>本类用 {@code @Component} 而不是配套的 {@code @EnableConfigurationProperties}：
 * 三个服务的启动类都扫 {@code com.aiticket.common}，直接让它被扫到最省事，
 * 不用在每个启动类上再挂一行注解。
 *
 * <p>字段全为空时不报错、也不拦启动 —— {@link OssStorage#isConfigured()} 返回 false，
 * 真正要上传时才给一句人话错误。这样没有 OSS 的环境（比如只想跑单测）也能起服务。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /** 形如 oss-cn-hangzhou.aliyuncs.com，不带协议头 */
    private String endpoint;

    /** bucket 名 */
    private String bucket;

    private String accessKeyId;

    private String accessKeySecret;
}
