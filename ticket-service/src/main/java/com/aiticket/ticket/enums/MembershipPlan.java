package com.aiticket.ticket.enums;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * 会员套餐 —— **价格与权益的唯一来源**。
 *
 * <p>价格与权益文案刻意不写在前端：改价不该要重新发版，套餐文案也不该散落在两个仓库
 * 层面上。前端一律从 {@code GET /api/v1/membership/plans} 取（见 MembershipController），
 * 所以加一档只要在这里加一个枚举值，前端一行都不用动。
 *
 * <p><b>三档（2026-10-15 起）</b>，一律 30 天一期 —— 「会员以月为单位」这条不变量
 * 刻意不为高档位破例，想续多久就续多久（续费从旧到期日往后接，见
 * {@code MembershipService.fulfill}）。
 *
 * <table>
 *   <caption>档位一览</caption>
 *   <tr><th>档位</th><th>价格</th><th>次数</th><th>单次成本</th><th>大厅优先曝光</th></tr>
 *   <tr><td>Lite</td><td>12 元</td><td>10</td><td>1.20</td><td>否</td></tr>
 *   <tr><td>Pro</td><td>30 元</td><td>30</td><td>1.00</td><td>否</td></tr>
 *   <tr><td>Max</td><td>78 元</td><td>120</td><td>0.65</td><td>是</td></tr>
 * </table>
 *
 * <p>两个变量的联动口径：<b>档位越高、单次成本越低</b>（12→10→6.5 毛），
 * 这是这三组数字唯一需要维持的关系。改价格时别把它改反了 —— 高价档若比低价档
 * 单价还贵，用户按档位表一算就会发现。
 *
 * <p>⚠️ 改这里的数字要同时改前端 {@code frontend/src/utils/ai-quota.ts} 的
 * {@code MEMBERSHIP_CREDITS} / {@code MEMBERSHIP_DAYS} ——
 * 那两个常量是登录卡片与工单详情页的**文案**（"开通会员可获得 30 次"），
 * 两边不一致会出现"说 30 次、实际发 20 次"这种用户一定会发现的错误。
 * （三档之后这两个常量只对 "Pro" 成立，前端理应改成从 {@code /plans} 现取。）
 *
 * <p>另外，订单表里存的是**下单那一刻**的 {@code plan_credits}/{@code plan_days} 快照，
 * 发放时用快照而不是读这个枚举 —— 否则改了套餐会把历史未支付订单按新配置发出去。
 * 「大厅优先曝光」同理是**发布工单那一刻**的快照（{@code ticket.member_promoted}），
 * 不随会员到期而收回。
 */
public enum MembershipPlan {

    /** 入门档：10 次 / 12 元。没有额外权益，卖点只是"门槛低" */
    LITE("Lite 会员", new BigDecimal("12.00"), 10, 30, false,
            "30 天内 10 次 AI 辅助开发额度"),

    /**
     * 中间档：30 次 / 30 元 —— **一期只有这一档时的原配置，价格与权益一字未改**。
     *
     * <p>它的前身枚举值叫 {@code MONTHLY_30}（库里存量会员与订单的 plan_code 都还是它），
     * 见 {@link #RESOLVE_ALIASES} 与 {@link #resolve}。
     */
    PRO("Pro 会员", new BigDecimal("30.00"), 30, 30, false,
            "30 天内 30 次 AI 辅助开发额度"),

    /**
     * 最高档：120 次 / 78 元，且**发布的工单在大厅优先曝光**。
     *
     * <p>优先曝光是唯一一项非次数权益，也是 Max 与 Pro 之间唯一"次数以外"的差别：
     * 大厅按 {@code member_promoted DESC, ai_prepaid DESC, created_at DESC} 排序，
     * 所以 Max 的单排在任何预附过额度的单之前。
     */
    MAX("Max 会员", new BigDecimal("78.00"), 120, 30, true,
            "30 天内 120 次 AI 辅助开发额度，发布的工单在大厅优先曝光");

    /**
     * 已下线档位码 → 现档位。**只用于读旧数据，不用于下单**。
     *
     * <p>{@code MONTHLY_30} 是 Pro 的前身：一期只有一档时枚举值就叫这个名字，
     * 库里的 {@code user_membership.plan_code} 与 {@code membership_order.plan_code}
     * 存量行全是它。若 {@link #of} 直接判它未知，一个 10-09 开通了会员的用户在
     * 会员页上会看到空档位 —— 而实际上他的权益一分不少。
     *
     * <p>{@link #of}（下单用）**刻意不认这些别名**：能买的只有当前在售的档位，
     * 一个过期的档位码不该还能下单。
     */
    private static final Map<String, MembershipPlan> RESOLVE_ALIASES = Map.of("MONTHLY_30", PRO);

    private final String name;
    private final BigDecimal price;
    private final int credits;
    private final int days;
    private final boolean hallPromoted;
    private final String description;

    MembershipPlan(String name, BigDecimal price, int credits, int days, boolean hallPromoted,
                   String description) {
        this.name = name;
        this.price = price;
        this.credits = credits;
        this.days = days;
        this.hallPromoted = hallPromoted;
        this.description = description;
    }

    /** 套餐标识，也是 plan_code 的取值 */
    public String code() {
        return name();
    }

    public String displayName() {
        return name;
    }

    public BigDecimal price() {
        return price;
    }

    public int credits() {
        return credits;
    }

    public int days() {
        return days;
    }

    /**
     * 该档位发布的工单是否在大厅优先曝光。
     *
     * <p>这是**权益的定义**，消费者是 {@code TicketService.createTicket} —— 它在建单时
     * 把结果快照进 {@code ticket.member_promoted}。大厅的排序只认那个列，不回头查会员，
     * 所以「这一档多给了什么」全仓只有这一处定义。
     */
    public boolean hallPromoted() {
        return hallPromoted;
    }

    public String description() {
        return description;
    }

    /**
     * 按 plan_code 查**在售**套餐，未知 code 返回空。
     *
     * <p>认别名的是 {@link #resolve}，不是这里 —— 下单路径必须走这个严格版本：
     * 已下线的档位码若能下单，前端一个陈旧的 localStorage 就能用旧价买到新权益。
     */
    public static Optional<MembershipPlan> of(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        for (MembershipPlan plan : values()) {
            if (plan.name().equals(code)) {
                return Optional.of(plan);
            }
        }
        return Optional.empty();
    }

    /**
     * 按 plan_code 查套餐，**连已下线的老档位码一起认**（{@code MONTHLY_30} → {@link #PRO}）。
     *
     * <p>读路径（我的会员、订单列表）用这个：那些 plan_code 是历史事实，不该因为
     * 后来调整了在售档位就查不出档。下单路径用 {@link #of}。
     */
    public static Optional<MembershipPlan> resolve(String code) {
        return of(code).or(() -> Optional.ofNullable(RESOLVE_ALIASES.get(code)));
    }

    /**
     * 把库里的档位码规范化成当前枚举名：存量的 {@code MONTHLY_30} 一律显示成 {@code PRO}。
     * 未知码原样返回 —— 显示一个认不出的字符串，总好过显示 null 或编一个档位出来。
     */
    public static String normalizeCode(String code) {
        return resolve(code).map(MembershipPlan::code).orElse(code);
    }

    /** 档位显示名，未知码退化成码本身。给 DTO 用，保证前端拿到的永远是可显示的字符串 */
    public static String displayNameOf(String code) {
        return resolve(code).map(MembershipPlan::displayName).orElse(code);
    }
}
