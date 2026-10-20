import LatticeLoader from '@/components/loaders/LatticeLoader'
import type { AiActionPhase } from '@/hooks/useTicketAi'

/**
 * AI 等待态：reactbits 的 LatticeLoader（`components/loaders/LatticeLoader`）在本站的
 * 唯一用法。
 *
 * 为什么要包一层而不是各处直接用 LatticeLoader：
 *
 * 1. **相位到状态的映射只写一次。** 队列里的 `AiActionPhase` 和组件的 `status`
 *    恰好同名（idle / working / done / error 去掉 idle），别处再写一次就迟早写反。
 * 2. **中文措辞只写一次。** 组件的三个 label 默认是英文（Thinking / Done in /
 *    Failed after），本站一律中文。
 * 3. 尺寸统一。AI 的等待发生在大抽屉和小气泡两种地方，前者给大一点。
 *
 * ⚠️ **调用方必须让它跨 working → done 保持挂载**（写成 `phase !== 'idle' && <AiWait/>`
 * 就行，别写成"working 时挂一个 Loading、done 时换一个对勾"）。原因是这个组件不是
 * 无状态的：`done` / `error` 时停在那一刻的耗时是**它自己一路数出来的**，卸载重挂
 * 秒表就从 0 重来，溶解动画也看不到。
 *
 * 无障碍：读屏器拿到的仍是组件的英文播报（"Done in 12.3 seconds"）—— 那段文案在
 * 上游组件内部拼死，本站没有覆写点。可见文字全是中文，这条只影响读屏。
 */
export function AiWait({
  phase,
  label,
  doneLabel = '完成，用时',
  errorLabel = '失败，用时',
  cellSize = 7,
  fontSize = 14,
  className
}: {
  /** `idle` 时**不渲染**任何东西（组件自己判断，调用方可以无条件放进来）。 */
  phase: AiActionPhase
  /** 进行中的动词，如「AI 正在生成分析与拆解」 */
  label: string
  /** 完成后的动词，紧跟冻结的耗时 */
  doneLabel?: string
  /** 失败后的动词，紧跟耗时 */
  errorLabel?: string
  /** 单个格子边长 px。气泡里用 5，大抽屉里用 7。 */
  cellSize?: number
  fontSize?: number
  className?: string
}) {
  if (phase === 'idle') return null
  return (
    <LatticeLoader
      status={phase}
      label={label}
      doneLabel={doneLabel}
      errorLabel={errorLabel}
      cellSize={cellSize}
      fontSize={fontSize}
      className={className}
    />
  )
}
