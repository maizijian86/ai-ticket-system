package com.aiticket.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${jwt.secret:aiticket-jwt-secret-key-change-in-production-min-256-bits}")
    private String jwtSecret;

    /** Access Token 过期。前端据此触发刷新流程。 */
    private static final int CODE_TOKEN_EXPIRED = 40102;
    /** 缺失 / 非法 / 类型不对。前端据此清空本地登录态并跳登录页。 */
    private static final int CODE_UNAUTHORIZED = 401;

    // logout 不在此列：它需要知道"要登出谁"，只能从已校验的 token 里取 userId。
    // 放在白名单里的话接口就必须自己接收 userId 参数，等于谁都能登出任何人。
    //
    // membership/callback 在列是因为那是**支付宝服务器**来 POST 的异步通知，它没有
    // Authorization 头。漏了这一条的后果值得单独记住：网关回 401 → 支付宝认为通知失败
    // → 重试几次后放弃 → 用户付了钱没到账，而日志里只有一条莫名其妙的 401
    // （连请求体都没有，因为被过滤器挡在最前面）。白名单是 startsWith 前缀匹配，
    // 写到 /callback 这一级就够，/callback/alipay 自动放行。
    private static final String[] WHITE_LIST = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/membership/callback",
            "/health",
            "/actuator/health"
    };

    /**
     * 匿名可读的路径：**只放行 GET**，且必须精确匹配。
     *
     * <p>访客要能浏览需求大厅和工单详情，就不该被网关一票否决。但放行的范围必须掐死：
     * <ul>
     *   <li>只放行这三个只读路径，其余一律要求登录 —— 尤其**不能**写成
     *       {@code /api/v1/tickets/**}：那会把 POST/PUT/DELETE 和三个 AI 接口一起放开，
     *       而 AI 接口是烧钱的；</li>
     *   <li>详情用 {@code \d+} 锚定，避免 {@code /stats}、{@code /my}、{@code /my/accepted}、
     *       {@code /completed} 这些同样以 {@code /api/v1/tickets/} 开头的接口被顺带放行。</li>
     *   <li>{@code /membership/plans} 是**价目表**，不含任何用户数据 ——
     *       {@code MembershipService.listPlans()} 连 userId 参数都没有，放行它不比公开一张
     *       价目表多泄露什么。而首页面向访客的套餐卡只能有这一个数据源：{@code MembershipPlan}
     *       的注释明确要求"价格与权益文案的唯一来源是后端，前端不写死"。**必须锚死这一条**，
     *       写成 {@code /api/v1/membership/**} 会连 {@code /me}（余额与到期日）、
     *       {@code /orders}（下单）一起放开。</li>
     *   <li>{@code /users/avatar} 与 {@code /users/avatars} 是登录卡的"边输边显示头像"
     *       和大厅卡片的发布者头像 —— 两者都发生在**没有登录态**的时候。放行范围必须
     *       锚死到这两条：写成 {@code /api/v1/users/**} 会连 {@code /users/{id}}（带 email）、
     *       {@code /users}（用户列表）、{@code /users/handlers} 一起放开。</li>
     * </ul>
     */
    private static final Pattern ANONYMOUS_READ_PATTERN = Pattern.compile(
            "^(/api/v1/tickets(/\\d+(/comments)?)?|/api/v1/membership/plans"
                    + "|/api/v1/users/(avatar|avatars))$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip authentication for white list
        for (String whitePath : WHITE_LIST) {
            if (path.startsWith(whitePath)) {
                return chain.filter(exchange);
            }
        }

        boolean anonymousReadable = isAnonymousReadable(request, path);
        String authHeader = request.getHeaders().getFirst("Authorization");
        log.debug("Authorization header: {}", authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (anonymousReadable) {
                // 访客放行。**必须抹掉客户端自带的身份头** —— 白名单分支是不经校验直接
                // 转发的，下游又拿 X-User-Id 决定要不要给看内部留言，不抹的话访客自己
                // 加一个 `X-User-Id: 1` 就能冒充发布者。
                return chain.filter(anonymous(exchange));
            }
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return unauthorized(exchange.getResponse(), CODE_UNAUTHORIZED, "请先登录");
        }

        // 带了 token 就照常校验，失败仍按原样报错（哪怕这个路径匿名可读）：
        // 过期 token 静默降级成访客，会让已登录用户看到"未登录"的界面，
        // 而且前端永远等不到 40102、刷新流程也就不会被触发。

        String token = authHeader.substring(7);
        log.debug("Token: {}...", token.length() > 50 ? token.substring(0, 50) : token);

        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 只有"确实过期"才回 40102。签名错、格式错都归 401，
            // 否则前端会拿着一个永远不会成功的坏 token 反复触发刷新。
            log.debug("Access token expired for path: {}", path);
            return unauthorized(exchange.getResponse(), CODE_TOKEN_EXPIRED, "Access Token 已过期");
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), CODE_UNAUTHORIZED, "Token 非法");
        }

        // refreshToken 是用同一个密钥签发的合法 JWT，不校验 tokenType 的话它能直接
        // 当 accessToken 用 —— 等于每个 token 都是 7 天，access 的短有效期就白设了。
        String tokenType = claims.get("tokenType", String.class);
        if (!"ACCESS".equals(tokenType)) {
            log.warn("Non-access token used on path: {} (tokenType={})", path, tokenType);
            return unauthorized(exchange.getResponse(), CODE_UNAUTHORIZED, "Token 类型不正确");
        }

        String username = claims.getSubject();
        Long userId = claims.get("userId", Long.class);
        String role = claims.get("role", String.class);

        // Add user info to headers (keep Authorization for downstream services)
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Name", username)
                .header("X-User-Role", role)
                .header("Authorization", authHeader)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /** 是不是"匿名可读"的请求：GET + 命中 {@link #ANONYMOUS_READ_PATTERN}。 */
    private static boolean isAnonymousReadable(ServerHttpRequest request, String path) {
        return HttpMethod.GET.equals(request.getMethod())
                && ANONYMOUS_READ_PATTERN.matcher(path).matches();
    }

    /**
     * 以访客身份放行：删掉客户端自带的身份头再转发。
     *
     * <p>删头不是洁癖 —— 下游服务信任网关注入的 {@code X-User-Id}（这是整套鉴权的地基），
     * 而这里的请求是**不经校验**直接转发的。只要客户端能自己塞这个头，地基就塌了：
     * 访客可以手写 {@code X-User-Id: 1} 冒充发布者，看到本该只有参与者可见的内部留言。
     */
    private static ServerWebExchange anonymous(ServerWebExchange exchange) {
        ServerHttpRequest cleaned = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Name");
                    headers.remove("X-User-Role");
                })
                .build();
        return exchange.mutate().request(cleaned).build();
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, int code, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -100; // High priority
    }
}
