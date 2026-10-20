import { Lock } from '@/components/icons'
import { Button } from '@/components/ui/button'

/**
 * AI 区域的遮罩：说清"为什么现在用不了"以及"怎么才能用"。
 *
 * 这一块的重要性在于**置灰的按钮什么也不说**，是最糟糕的一种"不可用"。所以本站的
 * AI 区域从不把按钮置灰了事，而是铺一层能读的说明：
 *
 *   - 访客          → 「登录后可用」+ 去登录按钮
 *   - 已登录非接单人 → 「接单后可用」+ 本工单有没有接单人（两种措辞见组件调用处）
 *   - 不是接单人时，发布者本人还要多看一句"预附的这 1 次买到了什么"
 *
 * 旧 Vue 里这是 `components/AiLockedHint.vue`，三处 tab 各挂一个。现在是共享抽屉，
 * 一个组件服务三个 tab。
 *
 * 样式上**刻意不用玻璃**：它铺在抽屉这块不透明白面上（见 AiWorkbench 的说明），
 * 玻璃在白底上等于没有。压在虹彩上的那些（右栏入口卡）才是玻璃。
 */
export function AiLockedHint({
  title,
  detail,
  actionText,
  onAction,
  className
}: {
  title: string
  /** 第二行说明。空串表示不显示这一行（如"正在加载额度信息…"）。 */
  detail?: string
  /** 给了才出现按钮 —— 有些场景没有出路（如"已由他人接单"），给个按钮只会让人白点。 */
  actionText?: string
  onAction?: () => void
  className?: string
}) {
  return (
    <div
      className={[
        'flex flex-col items-center justify-center gap-3 px-6 py-14 text-center',
        className ?? ''
      ].join(' ')}
    >
      <span className="flex size-11 items-center justify-center rounded-full border border-foreground/12 bg-muted text-foreground/70">
        <Lock size={18} />
      </span>
      <p className="text-base font-semibold text-foreground">{title}</p>
      {detail ? <p className="max-w-md text-sm leading-6 text-muted-foreground">{detail}</p> : null}
      {actionText && onAction ? (
        <Button onClick={onAction} className="mt-1">
          {actionText}
        </Button>
      ) : null}
    </div>
  )
}
