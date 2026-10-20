import type { Priority, TicketStatus } from '@/types'
import { getStatusLabel, getStatusType } from '@/utils/ticket-labels'

/**
 * 状态 / 优先级的**唯一**上色处。
 *
 * `utils/ticket-labels.ts` 里的 `getStatusType()` 返回的是 Element Plus 的色名
 * （'primary' | 'success' | 'warning' | 'danger' | 'info'），那套映射是纯函数、
 * 逻辑也还对，所以原样留着 —— 但颜色本身得换：EP 的 primary 是蓝色，而本项目的
 * 主色是那支紫。
 *
 * 于是这里做一次 5 → 5 的翻译。**只允许这一个文件消费 getStatusType** ——
 * 散着改必然导致标签颜色在二十个页面之间慢慢漂移，旧代码就是这么漂的
 * （同一个 REJECTED，六个页面显示三种颜色）。
 *
 * 浅色底上的配色约定：底是**极淡**的同色（白纸上压 6–10% 才不糊）、边是中调的
 * 同色、字取**深色**。三档一起走 —— 只把字改深而不动底色，淡底会显得脏；只把
 * 底加深而不改字，就回到了深色那套的用法。字用 -700 而不是 -900：整块标签很
 * 小，深到接近黑反而看不出是哪个色系了。
 */
const TONES: Record<string, string> = {
  // ACCEPTED「开发中」—— 主色，和按钮同一支紫
  primary: 'border-violet-300 bg-violet-100 text-violet-700',
  // COMPLETED「已完成」
  success: 'border-emerald-300 bg-emerald-50 text-emerald-700',
  // OPEN「待接单」/ PENDING_APPROVAL「待验收」—— 卡在某人的下一步，是提示不是错误
  warning: 'border-amber-300 bg-amber-50 text-amber-700',
  // REJECTED「已驳回」
  danger: 'border-red-300 bg-red-50 text-red-700',
  // CLOSED「已关闭」—— 唯一的无彩色，跟别的标签区分开
  info: 'border-border bg-muted text-muted-foreground'
}

export default function StatusTag({ status }: { status: TicketStatus }) {
  const tone = TONES[getStatusType(status)] ?? TONES.info
  return (
    <span
      className={[
        'inline-flex shrink-0 items-center rounded-full border px-2 py-0.5 text-xs font-medium whitespace-nowrap',
        tone
      ].join(' ')}
    >
      {getStatusLabel(status)}
    </span>
  )
}

/**
 * 优先级。P0 红 → P3 灰，只表达"多急"，不表达好坏 —— 所以用的是同一条色阶
 * 上的四档，而不是四个互不相干的语义色。
 */
const PRIORITY_TONES: Record<Priority, string> = {
  P0: 'border-red-300 bg-red-100 text-red-700',
  P1: 'border-amber-300 bg-amber-100 text-amber-700',
  P2: 'border-sky-300 bg-sky-100 text-sky-700',
  P3: 'border-border bg-muted text-muted-foreground'
}

export function PriorityTag({ priority }: { priority: Priority }) {
  return (
    <span
      className={[
        'inline-flex shrink-0 items-center rounded border px-1.5 py-0.5 font-mono text-[11px] font-medium',
        PRIORITY_TONES[priority] ?? PRIORITY_TONES.P3
      ].join(' ')}
    >
      {priority}
    </span>
  )
}
