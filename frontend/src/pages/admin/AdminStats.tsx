import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { ChartColumn, LoaderCircle, TriangleAlert } from '@/components/icons'
import { CARD_BASE, PAGE, PageHeader } from '@/components/layout/PageShell'
import { ticketApi } from '@/api/ticket'
import { userApi } from '@/api/user'
import { projectApi } from '@/api/project'
import { getStatusLabel } from '@/utils/ticket-labels'
import { cn } from '@/lib/utils'
import type { ProjectStatus, Role, TicketStatus } from '@/types'

/*
  统计分析（管理员）。

  ── 为什么是"数一下列表"而不是调一个统计接口 ──
  后端唯一的统计接口是 `GET /api/v1/tickets/stats`，但它返回的是**按当前用户视角**
  的口径（我接的单 / 我发布的单 / 我参与的单），`userId` 为空时只有"待接单"是全站的。
  管理员要看的是**全站分布**，那个接口给不出来。

  真正的选择是"加一个全局统计接口"还是"用现有列表接口的 total 聚合"。选了后者：
  `GET /tickets?status=X&pageSize=1` 返回的 `total` 就是该状态的全量计数，
  一次一个数字、零后端改动、口径与大厅完全一致（同一个 Specification 算出来的）。
  代价是 5+4+4 个请求 —— 管理后台的访问频率下这不构成问题，而且它们并发发出。

  真要上量（几万张单、需要时间序列），该做的是后端加聚合查询，不是在这页加缓存。
*/

/** 工单状态分布。顺序按业务流程排，不按枚举字母序 */
const TICKET_STATUSES: TicketStatus[] = [
  'OPEN',
  'ACCEPTED',
  'PENDING_APPROVAL',
  'COMPLETED',
  'REJECTED',
  'CLOSED'
]

/** 每条状态一个颜色。与 StatusTag 的语义色系对齐，但这里是"占比条"不是标签 */
const STATUS_BAR_COLORS: Record<TicketStatus, string> = {
  OPEN: 'bg-amber-400',
  ACCEPTED: 'bg-violet-400',
  PENDING_APPROVAL: 'bg-orange-400',
  COMPLETED: 'bg-emerald-400',
  REJECTED: 'bg-red-400',
  CLOSED: 'bg-zinc-400'
}

const ROLES: { value: Role; label: string }[] = [
  { value: 'PUBLISHER', label: '发布者' },
  { value: 'HANDLER', label: '接单者' },
  { value: 'ADMIN', label: '管理员' },
  { value: 'USER', label: '普通用户' }
]

const PROJECT_STATUSES: { value: ProjectStatus; label: string }[] = [
  { value: 'PLANNING', label: '规划中' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'ARCHIVED', label: '已归档' }
]

export default function AdminStats() {
  const navigate = useNavigate()

  const [ticketCounts, setTicketCounts] = useState<Record<string, number> | null>(null)
  const [roleCounts, setRoleCounts] = useState<Record<string, number> | null>(null)
  const [projectCounts, setProjectCounts] = useState<Record<string, number> | null>(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async () => {
    setFailed(false)
    try {
      // 并发发出：三组请求互不依赖，串行会让这页慢三倍
      const [tickets, roles, projects] = await Promise.all([
        Promise.all(
          TICKET_STATUSES.map((s) =>
            ticketApi
              .listTickets({ status: s, page: 1, pageSize: 1, includeAll: true })
              .then((r) => [s, r.total] as const)
          )
        ),
        Promise.all(
          ROLES.map((r) =>
            userApi.listUsers(1, 1, r.value).then((res) => [r.value, res.total] as const)
          )
        ),
        // 项目统计走后端现成的 /stats/count/{status}，逐条并发
        Promise.all(
          PROJECT_STATUSES.map((p) =>
            projectApi.countByStatus(p.value).then((n) => [p.value, n] as const)
          )
        )
      ])

      setTicketCounts(Object.fromEntries(tickets))
      setRoleCounts(Object.fromEntries(roles))
      setProjectCounts(Object.fromEntries(projects))
    } catch {
      // 项目那组最容易失败（它有 requireAuthLoader 之外的角色限制），
      // 失败时整页给一个可重试的状态，而不是半页有数字半页空白
      setFailed(true)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const ticketTotal = ticketCounts ? Object.values(ticketCounts).reduce((a, b) => a + b, 0) : 0
  const userTotal = roleCounts ? Object.values(roleCounts).reduce((a, b) => a + b, 0) : 0
  const projectTotal = projectCounts ? Object.values(projectCounts).reduce((a, b) => a + b, 0) : 0

  return (
    <div className={PAGE}>
      <AnimatedContent trigger="mount" distance={20} duration={0.5} ease="power2.out" threshold={0}>
        <PageHeader
          eyebrow="Admin · Stats"
          title="统计分析"
          desc="全站口径：工单状态分布、用户构成、项目进度。点任一数字可跳到对应的列表。"
          actions={
            <button
              type="button"
              onClick={() => void load()}
              className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-full border border-border bg-card px-3.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              <ChartColumn size={14} />
              刷新
            </button>
          }
        />
      </AnimatedContent>

      {failed ? (
        <div className={cn(CARD_BASE, 'mt-6 flex flex-col items-center gap-3 px-6 py-12 text-center')}>
          <TriangleAlert size={20} className="text-amber-600" />
          <p className="text-sm text-muted-foreground">统计没加载出来 —— 后端可能没起，或这个账号不是管理员。</p>
          <button
            type="button"
            onClick={() => void load()}
            className="cursor-pointer rounded-full border border-border bg-card px-4 py-1.5 text-sm font-medium transition-colors hover:bg-accent"
          >
            重试
          </button>
        </div>
      ) : (
        <div className="mt-6 grid gap-4 lg:grid-cols-2">
          {/* ── 工单状态分布 ── */}
          <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" threshold={0}>
            <section className={cn(CARD_BASE, 'h-full p-6')}>
              <div className="flex items-baseline justify-between">
                <h2 className="text-sm font-semibold">工单状态分布</h2>
                <span className="text-xs text-muted-foreground">
                  {ticketCounts ? `共 ${ticketTotal} 张` : '…'}
                </span>
              </div>

              {!ticketCounts ? (
                <Skeleton rows={6} />
              ) : (
                <>
                  {/* 一条总占比条：比六张孤立数字更能看出盘子怎么分的 */}
                  <div className="mt-4 flex h-2.5 w-full overflow-hidden rounded-full bg-muted">
                    {TICKET_STATUSES.map((s) => {
                      const pct = ticketTotal ? ((ticketCounts[s] ?? 0) / ticketTotal) * 100 : 0
                      if (pct === 0) return null
                      return (
                        <div
                          key={s}
                          className={STATUS_BAR_COLORS[s]}
                          style={{ width: `${pct}%` }}
                          title={`${getStatusLabel(s)} ${ticketCounts[s] ?? 0}`}
                        />
                      )
                    })}
                  </div>

                  <ul className="mt-4 divide-y divide-border">
                    {TICKET_STATUSES.map((s) => {
                      const count = ticketCounts[s] ?? 0
                      const pct = ticketTotal ? Math.round((count / ticketTotal) * 100) : 0
                      return (
                        <li key={s} className="flex items-center gap-3 py-2.5">
                          <span className={cn('size-2.5 shrink-0 rounded-full', STATUS_BAR_COLORS[s])} />
                          <button
                            type="button"
                            onClick={() => navigate('/tickets')}
                            className="flex-1 cursor-pointer text-left text-sm transition-colors hover:text-foreground/70"
                          >
                            {getStatusLabel(s)}
                          </button>
                          <span className="font-mono text-xs text-muted-foreground">{pct}%</span>
                          <span className="w-10 text-right text-sm font-semibold tabular-nums">{count}</span>
                        </li>
                      )
                    })}
                  </ul>
                </>
              )}
            </section>
          </AnimatedContent>

          <div className="flex flex-col gap-4">
            {/* ── 用户构成 ── */}
            <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" delay={0.06} threshold={0}>
              <section className={cn(CARD_BASE, 'p-6')}>
                <div className="flex items-baseline justify-between">
                  <h2 className="text-sm font-semibold">用户构成</h2>
                  <span className="text-xs text-muted-foreground">
                    {roleCounts ? `共 ${userTotal} 人` : '…'}
                  </span>
                </div>

                {!roleCounts ? (
                  <Skeleton rows={4} />
                ) : (
                  <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                    {ROLES.map((r) => (
                      <button
                        key={r.value}
                        type="button"
                        onClick={() => navigate('/admin/users')}
                        className="cursor-pointer rounded-xl border border-border bg-background px-3 py-3 text-left transition-colors hover:bg-muted/60"
                      >
                        <div className="text-lg font-semibold tabular-nums">{roleCounts[r.value] ?? 0}</div>
                        <div className="mt-0.5 text-[11px] text-muted-foreground">{r.label}</div>
                      </button>
                    ))}
                  </div>
                )}
              </section>
            </AnimatedContent>

            {/* ── 项目进度 ── */}
            <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" delay={0.1} threshold={0}>
              <section className={cn(CARD_BASE, 'p-6')}>
                <div className="flex items-baseline justify-between">
                  <h2 className="text-sm font-semibold">项目进度</h2>
                  <span className="text-xs text-muted-foreground">
                    {projectCounts ? `共 ${projectTotal} 个` : '…'}
                  </span>
                </div>

                {!projectCounts ? (
                  <Skeleton rows={4} />
                ) : projectTotal === 0 ? (
                  <p className="mt-4 text-sm text-muted-foreground">
                    还没有项目。项目模块的前端页面尚未开放，但接口与数据都在。
                  </p>
                ) : (
                  <ul className="mt-4 space-y-2.5">
                    {PROJECT_STATUSES.map((p) => {
                      const count = projectCounts[p.value] ?? 0
                      const pct = projectTotal ? (count / projectTotal) * 100 : 0
                      return (
                        <li key={p.value}>
                          <div className="flex items-baseline justify-between text-sm">
                            <span>{p.label}</span>
                            <span className="font-mono text-xs text-muted-foreground">
                              {count} · {Math.round(pct)}%
                            </span>
                          </div>
                          <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                            <div className="h-full rounded-full bg-foreground/70" style={{ width: `${pct}%` }} />
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                )}
              </section>
            </AnimatedContent>
          </div>
        </div>
      )}
    </div>
  )
}

/** 加载骨架。行高与真实行对齐，避免数据回来时整块跳一下。 */
function Skeleton({ rows }: { rows: number }) {
  return (
    <div className="mt-4 space-y-3">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-3">
          <div className="h-3 flex-1 animate-pulse rounded bg-muted" />
          <LoaderCircle size={12} className="animate-spin text-muted-foreground/40" />
        </div>
      ))}
    </div>
  )
}
