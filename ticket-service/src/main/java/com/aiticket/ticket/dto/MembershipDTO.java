package com.aiticket.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的会员状态 + 账户余额（{@code GET /api/v1/membership/me}）。
 *
 * <p>两件事一起返回，是因为前端要的是"我是不是会员 + 我还剩几次"这一个组合 ——
 * 分两个接口会让个人主页先渲染出"还剩 0 次"再跳成"还剩 35 次"。
 *
 * <p>{@code remaining} 在**非会员时也照常返回真实余额**：所有注册用户都持有那 5 次
 * 永久额度，返回 0 会让页面显示"你还剩 0 次"，是错的。
 */
@Data
@Builder
public class MembershipDTO {

    /** 实时判定（status=ACTIVE 且 expires_at > now），不是任何缓存字段 */
    private Boolean isMember;

    /**
     * 非会员时全为 null，但 remaining 照常为真实余额。
     *
     * <p>**已规范化**：存量会员的 {@code MONTHLY_30}（Pro 的前身）在这里一律是
     * {@code PRO}，前端不必认识历史档位码。
     */
    private String planCode;

    /** 档位显示名（"Pro 会员"），前端直接渲染，不用自己维护码 → 名的映射 */
    private String planName;

    private LocalDateTime startedAt;

    private LocalDateTime expiresAt;

    private Integer creditsGranted;

    /** 账户可用次数（未过期批次之和）。非会员时就是注册赠送剩下的那几次 */
    private Integer remaining;

    /**
     * 最近一次过期时间，只在**可用批次**里取。
     * 若从全部批次里取最小值，会员到期后会提示一个已经过去的日期。
     */
    private LocalDateTime nextExpireAt;
}
