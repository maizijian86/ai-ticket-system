import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Clock } from '@/components/icons'
import { CARD_BASE } from '@/components/layout/PageShell'

/**
 * 「还没做」的页面。
 *
 * ⚠️ 文案在 2026-10 整体改写过一次，别再改回去：这里原来写的是
 * "这个页面还在从 Vue 迁到 React 的路上"。那句话当时是真的，但迁移已经收尾
 *（59 个 .vue 全删了，见 Phase 2），现在只有**功能本身没做**这一种情况，
 * 再挂那句就是误导 —— 用户会以为站点半成品，而不是"这个模块还没开放"。
 *
 * 剩下的用途只有一处：/projects（项目管理）。后端 35 个接口是完整可用的，
 * 缺的是前端三个页面，属于单独排期的工作量，不在本轮范围内。
 */
export default function Placeholder({ title }: { title: string }) {
  const navigate = useNavigate()

  return (
    <div className="mx-auto flex min-h-[calc(100vh-56px)] w-full max-w-[720px] flex-col items-center justify-center px-6 py-16">
      <div className={`${CARD_BASE} w-full px-8 py-14 text-center`}>
        <div className="mx-auto flex size-14 items-center justify-center rounded-full border border-border bg-muted text-muted-foreground">
          <Clock size={22} />
        </div>

        <p className="mt-5 font-mono text-[11px] font-medium tracking-[0.24em] text-muted-foreground uppercase">
          Coming soon
        </p>
        <h1 className="mt-2 text-xl font-semibold tracking-tight sm:text-2xl">{title}</h1>
        <p className="mx-auto mt-3 max-w-sm text-sm leading-6 text-muted-foreground">
          这个模块还没开放。需求大厅、工单详情、AI 辅助与会员购买都已经可以正常使用。
        </p>

        <button
          type="button"
          onClick={() => navigate('/tickets')}
          className="mt-7 inline-flex h-10 cursor-pointer items-center justify-center gap-1.5 rounded-full border border-border bg-card px-5 text-sm font-medium transition-colors hover:bg-accent"
        >
          <ArrowLeft size={15} />
          去需求大厅
        </button>
      </div>
    </div>
  )
}
