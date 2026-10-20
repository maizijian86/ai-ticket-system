import { cn } from 'cn'

import { GlassLayer } from '@/components/ui/glass-button'
import { Lock } from '@/components/icons'

/**
 * 详情页右栏那三张 AI 入口卡（分析 / 对话 / PRD）。
 *
 * 它同时是**入口**和**预览**：卡片上露出已有产出的头几行，点一下把同一个工作台
 * （components/ai/AiWorkbench.tsx）拉到屏幕下方，并直接落在对应的那一页 ——
 * 卡片给"有什么"，抽屉给"全部"。
 *
 * 三条来自大厅筛选卡的硬规则，这里一样适用（都是玻璃，踩过一遍了）：
 *
 * 1. **内边距写在内层**。玻璃要铺满整个盒子（含内边距），只铺内容那一圈会在四周
 *    留出一圈白边。
 * 2. **要表达"不可用"不能让祖先透明**。给外层的按钮加 opacity 会形成 backdrop root，
 *    折射被关进按钮内部、干净的玻璃退成一块糊的 —— 只能压**内层内容**的不透明度。
 * 3. **文字不能用 text-muted-foreground**。那支中灰压在饱和的蓝虹彩上几乎化在背景里
 *    （大厅那次实测过），一律用 text-foreground/70 起步。
 *
 * ⚠️ **预览也是一段 AI 生成内容，访客与非接单人必须遮住。** 后端的 AI 字段是随工单
 * 一起返回的（不怎么鉴权），所以"该不该给这个人看"只能在渲染这一层挡：调用方拿不到
 * 可见性时，`preview` 要传那句邀请语、绝不能传 `ticket.aiTechSuggestion`。这是新增的
 * 第二处判断点（另一处是抽屉里的 tab 内容），漏一个就等于把 AI 产出漏给了访客。
 */
export function AiRailCard({
  title,
  icon: Icon,
  preview,
  status,
  ready = false,
  locked = false,
  onClick
}: {
  title: string
  icon: typeof Lock
  /** 预览文本。没有产出时给一句邀请语（调用方定）。 */
  preview: string
  /** 右上角那行小字，如「剩余 2 次」「已用」。 */
  status?: string
  /** 是否已有产出（决定要不要亮那个小圆点与"已有内容"）。 */
  ready?: boolean
  /** 不可用（访客 / 非接单人）。仍然是可点的 —— 点开抽屉会看到完整的原因说明。 */
  locked?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        // `h-full`：详情页给每张卡套了一层动画包裹层（它才是网格项、会被 stretch），
        // 按钮必须跟着填满，否则同一行三张玻璃各按内容高度参差。
        'relative h-full w-full cursor-pointer rounded-[1.25rem] border text-left transition-colors',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/50',
        locked ? 'border-foreground/15' : 'border-foreground/25 hover:border-foreground/40'
      )}
    >
      <GlassLayer radius={20} />
      {/* 内边距在内层 —— 见文件头第 1 条 */}
      <span className={cn('relative z-10 flex flex-col gap-2.5 px-5 py-4', locked && 'opacity-70')}>
        <span className="flex items-center gap-2.5">
          <span className="flex size-8 shrink-0 items-center justify-center rounded-xl bg-foreground/12 text-foreground">
            <Icon size={15} />
          </span>
          <span className="text-sm font-semibold text-foreground">{title}</span>
          {ready && (
            <span
              aria-label="已有内容"
              className="size-1.5 shrink-0 rounded-full bg-foreground/45"
            />
          )}
          <span className="ml-auto flex items-center gap-1.5">
            {status ? <span className="text-xs text-foreground/70">{status}</span> : null}
            {locked ? <Lock size={13} className="text-foreground/70" /> : null}
          </span>
        </span>
        <span className="line-clamp-3 text-xs leading-5 whitespace-pre-wrap text-foreground/70">
          {preview}
        </span>
      </span>
    </button>
  )
}
