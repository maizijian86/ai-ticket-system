import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/**
 * 页面外壳与卡片的**共享配方**。
 *
 * 为什么要有这个文件：`CARD_BASE` 这串投影/圆角/边框原本在 TicketDetail 里是局部常量，
 * 迁 React 之后又要写 6 个新页面 —— 每个页面各抄一遍的话，调的时必然漏掉几处，
 * 最后是"六张卡有五种投影"（这个项目在 Vue 时代已经吃过一次同样的亏：
 * 同一个 REJECTED 在 6 个页面显示三种颜色，见 utils/ticket-labels.ts 的文件注释）。
 *
 * 所以：**新页面一律从这里取**，不要在页面里重新写字面量。
 */

/** 页面内容宽度。大厅/详情/会员/个人主页都用这一档。 */
export const PAGE = 'mx-auto w-full max-w-[1200px] px-6 py-8'

/** 窄版容器：结果页、表单页这类读完就走的单列页面。 */
export const NARROW = 'mx-auto w-full max-w-[720px] px-6 py-10'

/** 白卡的基础外观。圆角、边、投影与大厅的工单卡同一套值。 */
export const CARD_BASE =
  'rounded-2xl border border-border bg-card shadow-[0_1px_2px_rgba(24,24,27,0.07),0_16px_36px_-18px_rgba(24,24,27,0.3)]'

/** 小圆角标签（技术栈、状态补充说明）。 */
export const CHIP = 'inline-flex items-center rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground'

/** 等宽小标签（技术栈那类代码感的内容）。 */
export const MONO_CHIP = 'rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground'

/**
 * 页头：一条等宽大写 eyebrow + 大标题 + 可选说明 + 右侧动作区。
 *
 * eyebrow 用英文（Requirement Hall / Membership / Profile）是既有的视觉语言，
 * 见大厅与首页 —— 直接沿用，别换成中文，否则跟首页那条 `REQUIREMENT HALL` 打架。
 *
 * ── 关于对比度（这一版动过的唯一一处）──
 * 页头是**直接压在虹彩背景上**的（不套白卡，与首页/大厅一致）。而虹彩是高饱和蓝，
 * `text-muted-foreground` 那种中性灰压上去会明显发虚，正文观感像没加载完。
 * 所以这里把文字提亮一档（`foreground/70`）并在浅色底上加一层极淡的白色投影 ——
 * 投影只做"把笔画从彩底上托起来"，不要加深色阴影（深色阴影在白卡上会脏）。
 */
export function PageHeader({
  eyebrow,
  title,
  desc,
  actions
}: {
  eyebrow: string
  title: string
  desc?: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <p className="font-mono text-xs font-medium tracking-[0.24em] text-foreground/55 uppercase [text-shadow:0_1px_0_rgba(255,255,255,0.5)]">
          {eyebrow}
        </p>
        <h1 className="mt-1.5 text-2xl font-semibold tracking-tight [text-shadow:0_1px_0_rgba(255,255,255,0.35)] sm:text-3xl">
          {title}
        </h1>
        {desc ? (
          <p className="mt-2 max-w-2xl text-sm leading-6 text-foreground/70 [text-shadow:0_1px_0_rgba(255,255,255,0.45)]">
            {desc}
          </p>
        ) : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  )
}

/** 白卡里的一节：标题 + 内容。详情页与各表单页共用。 */
export function SectionCard({
  title,
  icon: Icon,
  actions,
  children,
  className
}: {
  title: string
  icon?: React.ComponentType<{ size?: number | string; className?: string }>
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={cn(CARD_BASE, 'p-6', className)}>
      <div className="flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          {Icon ? <Icon size={15} className="text-muted-foreground" /> : null}
          {title}
        </h2>
        {actions}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  )
}
