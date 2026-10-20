import PixelCard from '@/components/cards/PixelCard'
import { Calendar, Check, Coins, Send, Ticket } from '@/components/icons'
import { cn } from '@/lib/utils'
import type { MembershipPlanDTO } from '@/types'

/**
 * 会员套餐卡 —— 首页第二屏那三张（Lite / Pro / Max）。
 *
 * 数据**全部来自后端** `GET /api/v1/membership/plans`：档位、价格、次数、天数、
 * 优惠文案、有没有「大厅优先曝光」都由 `MembershipPlan` 枚举下发。这里一个数字都不写死
 * —— 后端加一档、改一次价，这张卡自动跟着变（前端唯一要动的是栅格列数）。
 *
 * 卡片本体用 reactbits 的 `PixelCard`（vendored 在 components/cards/）：白卡 +
 * 一层常驻的像素点阵。**参数里有两处是动过的**，都写在下面 PixelCard 的调用处。
 */

type PlanCardProps = {
  plan: MembershipPlanDTO
  /** 推荐档：加一圈墨色描边和一枚「推荐」角标。目前由调用方指定（Pro） */
  featured?: boolean
  /** 滚入视口后错开一点再揭示，一排三张不至于同时炸开 */
  delay?: number
  /** 点了「开通」。visitor 传进来的行为由调用方决定（弹登录卡） */
  onPick: (plan: MembershipPlanDTO) => void
  /** 按钮文案 */
  actionLabel: string
}

/** 12.00 → 12、12.50 → 12.5。后端价格是 BigDecimal，序列化成 JSON 数字会掉末尾的 0 */
function yuan(value: number) {
  return value.toFixed(2).replace(/\.00$/, '')
}

export function PlanCard({ plan, featured = false, delay = 0, onPick, actionLabel }: PlanCardProps) {
  const perCredit = plan.credits > 0 ? plan.price / plan.credits : 0

  return (
    <PixelCard
      /*
        示例给的是深色主题那套（`colors` 取 `#f8fafc` 系、`theme="dark"`）。本站是白底，
        同一批近白像素压在白卡上等于没画，所以**只换了两处颜色**，其余参数逐字照抄：
          · theme  → light（组件自带白面渐变 + 浅色投影）
          · colors / glowColor → 换成组件自带的 light 调色板，并取偏蓝的那一组
            （VARIANTS.blue.light），跟全站背景的蓝一致
        gap / speed / pixelSize / shape / origin / revealDuration / spotlight / glow /
        opacity / fade / trigger / radius / clickPulse / intro 全部是示例里的原值。
      */
      theme="light"
      colors={['#0c4a6e', '#0369a1', '#0284c7']}
      glowColor="#0284c7"
      shape="square"
      origin="center"
      gap={5}
      pixelSize={2}
      speed={35}
      revealDuration={600}
      spotlight={0.5}
      glow={0.5}
      opacity={0.35}
      fade={0.7}
      trigger="always"
      radius={24}
      clickPulse
      intro
      introDelay={delay}
      /* 宽高只能走内联样式：`.pixel-card` 把 300×400 写在类里了 */
      style={{ width: '100%', height: '100%' }}
      className={cn(
        'h-full w-full',
        featured && 'shadow-[0_0_0_2px_var(--foreground),0_28px_56px_-28px_rgba(24,24,27,0.45)]'
      )}
    >
      <div className="flex h-full w-full flex-col p-7 text-left">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold tracking-tight">{plan.name}</span>
          {featured && (
            <span className="rounded-full bg-foreground px-2 py-0.5 text-[11px] font-medium text-background">
              推荐
            </span>
          )}
          {/* 「优先曝光」是后端下发的一项**权益**，不是按档位名写死的文案 */}
          {plan.hallPromoted && !featured && (
            <span className="rounded-full border border-foreground/20 px-2 py-0.5 text-[11px] text-muted-foreground">
              优先曝光
            </span>
          )}
        </div>

        <div className="mt-6 flex items-baseline gap-1.5">
          <span className="text-4xl font-semibold tracking-tight">
            <span className="mr-0.5 text-xl">¥</span>
            {yuan(plan.price)}
          </span>
          <span className="text-xs text-muted-foreground">/ {plan.days} 天</span>
        </div>
        {/* 三档之间唯一需要维持的关系：档位越高、单次越便宜。这里把它算给用户看 */}
        <div className="mt-1.5 text-xs text-muted-foreground">约 {perCredit.toFixed(2)} 元 / 次</div>

        <div className="mt-6 h-px w-full bg-foreground/10" />

        <ul className="mt-5 flex flex-col gap-2.5 text-sm">
          <li className="flex items-center gap-2">
            <Coins size={15} className="shrink-0 text-foreground/60" />
            {plan.credits} 次 AI 辅助开发额度
          </li>
          <li className="flex items-center gap-2">
            <Calendar size={15} className="shrink-0 text-foreground/60" />
            有效期 {plan.days} 天，到期前续期可叠加
          </li>
          {plan.hallPromoted && (
            <li className="flex items-center gap-2">
              <Ticket size={15} className="shrink-0 text-foreground/60" />
              发布的工单在大厅优先曝光
            </li>
          )}
          {/* 次数能花在哪些地方 —— 这是「额度」这个词唯一被解释清楚的地方 */}
          <li className="flex items-start gap-2">
            <Check size={15} className="mt-0.5 shrink-0 text-foreground/60" />
            <span>技术分析、PRD 文档、任务拆解与 AI 开发对话</span>
          </li>
        </ul>

        <p className="mt-4 text-xs leading-5 text-muted-foreground">{plan.desc}</p>

        <div className="mt-auto pt-6">
          <button
            type="button"
            onClick={() => onPick(plan)}
            className={cn(
              'inline-flex h-10 w-full cursor-pointer items-center justify-center gap-2 rounded-full text-sm font-semibold transition-colors',
              'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/50',
              featured
                ? 'bg-foreground text-background hover:bg-foreground/85'
                : 'border border-border bg-card hover:bg-accent'
            )}
          >
            <Send size={15} />
            {actionLabel}
          </button>
        </div>
      </div>
    </PixelCard>
  )
}
