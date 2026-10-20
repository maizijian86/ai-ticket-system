package com.aiticket.common.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aiticket.common.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Date;

/**
 * 阿里云 OSS 的读写入口。放 common 是为了**一份 AK 全家用** —— 头像在 user-service、
 * 工单封面在 ticket-service、生图转存在 ai-service，三个服务都要发请求，各配一套 AK
 * 迟早会出现"某一个服务的 key 过期了没人发现"。
 *
 * <p><b>客户端是懒建的，不是构造时就建。</b> 两个原因：
 * <ul>
 *   <li>没有配 OSS 的环境（只想跑单测、只想起网关）不该因为缺配置起不来；</li>
 *   <li>{@code OSSClientBuilder().build()} 传空 endpoint 会当场抛
 *       IllegalArgumentException —— 那是个启动期异常，堆栈里看不出"是配置没填"。</li>
 * </ul>
 * 懒建之后，错误推迟到真正要上传时，由 {@link #client()} 给一句人话。
 *
 * <p>OSSClient 是**线程安全**的（官方文档明确写了），所以这里全局一个实例、double-check
 * 懒加载即可，别每个请求 new 一个 —— 它内部持有连接池和 HTTP 客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssStorage {

    private final OssProperties properties;

    /** 双重检查锁的可见性靠它，不用 AtomicReference —— 这里没有 CAS 需求，只有"建一次" */
    private volatile OSS client;

    /**
     * 上传一个对象。内容全在内存里 —— 调用方传 byte[]，所以**只适合小文件**
     * （头像、封面这类）。将来要传大附件得改成流式，别直接拿这个方法套。
     */
    public void upload(String key, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new BusinessException(400, "上传内容为空");
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }
        client().putObject(properties.getBucket(), key, new ByteArrayInputStream(content), metadata);
        log.info("OSS 上传成功: bucket={}, key={}, size={}B", properties.getBucket(), key, content.length);
    }

    /**
     * 删除一个对象。**失败只告警、不抛异常** —— 调用它的地方是"换了新头像，把旧的删掉"，
     * 此时新头像已经写进库了；为了一个删不掉的旧对象让整个换头像请求失败，是把小事搞成大事。
     * 代价是可能留一个孤儿对象，可接受。
     */
    public void delete(String key) {
        if (key == null || key.isBlank() || !isConfigured()) {
            return;
        }
        try {
            client().deleteObject(properties.getBucket(), key);
            log.info("OSS 删除成功: bucket={}, key={}", properties.getBucket(), key);
        } catch (Exception e) {
            log.warn("OSS 删除失败（已忽略，可能留下孤儿对象）: key={}, reason={}", key, e.getMessage());
        }
    }

    /**
     * 对象的公网地址。**前提是 bucket 是公共读**，否则这个地址访问会 403。
     * 私有读的做法见 {@link #signedUrl}。
     */
    public String publicUrl(String key) {
        if (key == null || key.isBlank() || !isConfigured()) {
            return null;
        }
        return "https://" + properties.getBucket() + "." + properties.getEndpoint() + "/" + key;
    }

    /**
     * 带签名的临时地址，给**私有读**的 bucket 用。
     *
     * <p>本次没有调用方 —— 当前的口径是 bucket 公共读、头像直接拼 {@link #publicUrl}。
     * 留着它是因为"哪天不想公开了"是个很可能发生的决定，到那时只需要把拼地址的那几行
     * 换成这个方法，不用重新查一遍签名怎么写。
     *
     * @param seconds 有效期，秒
     */
    public String signedUrl(String key, long seconds) {
        if (key == null || key.isBlank() || !isConfigured()) {
            return null;
        }
        Date expiration = new Date(System.currentTimeMillis() + seconds * 1000L);
        return client().generatePresignedUrl(properties.getBucket(), key, expiration).toString();
    }

    /** 配置是否齐全。缺任何一项都算没配好。 */
    public boolean isConfigured() {
        return notBlank(properties.getEndpoint())
                && notBlank(properties.getBucket())
                && notBlank(properties.getAccessKeyId())
                && notBlank(properties.getAccessKeySecret());
    }

    private OSS client() {
        if (!isConfigured()) {
            // 明确点出缺哪一项，否则运维只能对着 "OSS 未配置" 干瞪眼
            throw new BusinessException(500,
                    "OSS 未配置，缺少: " + missingConfigHint()
                            + "（对应环境变量 OSS_ENDPOINT / OSS_BUCKET / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET）");
        }
        OSS local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = new OSSClientBuilder().build(
                            properties.getEndpoint(),
                            properties.getAccessKeyId(),
                            properties.getAccessKeySecret());
                    client = local;
                }
            }
        }
        return local;
    }

    private String missingConfigHint() {
        StringBuilder sb = new StringBuilder();
        if (!notBlank(properties.getEndpoint())) sb.append("aliyun.oss.endpoint ");
        if (!notBlank(properties.getBucket())) sb.append("aliyun.oss.bucket ");
        if (!notBlank(properties.getAccessKeyId())) sb.append("accessKeyId ");
        if (!notBlank(properties.getAccessKeySecret())) sb.append("accessKeySecret ");
        return sb.toString().trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 关容器时释放连接池。不写这个的话，配合 spring-boot-devtools 热重启会一次次
     * 泄漏 HTTP 连接，跑到几十次之后 OSS 请求开始超时 —— 症状离原因很远。
     */
    @PreDestroy
    public void shutdown() {
        OSS local = client;
        if (local != null) {
            local.shutdown();
            log.info("OSS 客户端已关闭");
        }
    }
}
