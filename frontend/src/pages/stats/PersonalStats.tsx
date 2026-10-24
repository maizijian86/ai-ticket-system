import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { CARD_BASE, CHIP, PAGE, PageHeader } from '@/components/layout/PageShell'
import { GlassButton } from '@/components/ui/glass-button'
import { ChartColumn, Coins, LoaderCircle, TriangleAlert, Wallet } from '@/components/icons'
import { ticketApi } from '@/api/ticket'
import { membershipApi } from '@/api/membership'
import { useAuthStore } from '@/stores/authStore'
import { formatAmount } from '@/utils/order-labels'
import { cn } from '@/lib/utils'
import type { MembershipDTO, Role, TicketStatsDTO } from '@/types'

/*
  个人信息统计 —— 回答"我干得怎么样"。

  ── 和另外两个页面划清边界 ──
    /profile  我是谁     ：头像、昵称、额度、订单、接单者技能
    /stats    我干了什么 ：发布侧/接单侧的数量与进度、完成率、累计金额
    /wallet   我有多少钱 ：余额、冻结、充值、流水

  所以这里**不放**余额（那是 /wallet 的职责，只给一个入口）——
  两处都显示余额，一个走实时接口、一个走统计快照，迟早对不上。

  ── 数字口径（全部来自 /tickets/stats）──
  发布侧看 creatorId，接单侧看 handlerId，两边完全对称。
  完成率的分母用"总发布/总接单"，**分母为 0 时显示 — 而不是 0%**：
  0/0 没有意义，显示 0% 会让人以为"一次都没完成"。

  ── 金额为什么不再叫"预算" ──
  钱包上线前系统里没有资金流，当时只能用 SUM(budget) 凑一个名义金额。
  现在 `settledIncome` / `settledPayout` 是**真实结算**记录：
  收入 = 我接的单验收通过后进钱包的钱，支出 = 我发布的单结算给对方的钱。
  历史工单（钱包上线前创建）没有托管、从未结算，对这两个数字的贡献是 0 —— 这是对的。
*/

const ROLE_LABELS: Record<Role, string> = {
  PUBLISHER: '发布者',
  HANDLER: '接单者',
  ADMIN: '管理员'
}

export default function PersonalStats() {
  const navigate = useNavigate()
  const userInfo = useAuthStore((s) => s.userInfo)

  const [stats, setStats] = useState<TicketStatsDTO | null>(null)
  const [me, setMe] = useState<MembershipDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setFailed(false)
    try {
      // 两个接口互不依赖，并发发。会员那个失败**不该**让整页挂掉 ——
      // 它是配角（额度/会员状态），统计数字才是这一页的主体。
      const [s, m] = await Promise.all([
        ticketApi.getStats(),
        membershipApi.me().catch(() => null)
      ])
      setStats(s)
      setMe(m)
    } catch {
      setFailed(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const role = userInfo?.role
  const isHandler = role === 'HANDLER'

  /** 完成率。分母 0 返回 null（显示 —），返回值是 0..100 的整数 */
  const rate = (done: number, total: number) => (total > 0 ? Math.round((done / total) * 100) : null)

  const handlerRate = stats ? rate(stats.completed, stats.handledTotal) : null
  const creatorRate = stats ? rate(stats.completedAsCreator, stats.createdTotal) : null

  /** 新用户：两边都没发过也没接过 → 空态引导，而不是一页 0 */
  const isNewbie = stats != null && stats.createdTotal === 0 && stats.handledTotal === 0

  if (failed) {
    return (
      <div className={PAGE}>
        <div className={cn(CARD_BASE, 'flex flex-col items-center gap-4 p-10 text-center')}>
          <TriangleAlert className="size-8 text-muted-foreground" />
          <div>
            <p className="font-medium">统计没加载出来</p>
            <p className="mt-1 text-sm text-muted-foreground">可能是网络问题，重试一下</p>
          </div>
          <GlassButton onClick={() => void load()} className="px-5 py-2 text-sm">
            重试
          </GlassButton>
        </div>
      </div>
    )
  }

  return (
    <div className={PAGE}>
      <AnimatedContent distance={20} duration={0.5} ease="power2.out">
        <PageHeader
          eyebrow="MY STATS"
          title="个人信息统计"
          desc={
            isHandler
              ? '你接的单与交付进度、完成率，以及实际到账的钱。'
              : '你发布的需求与验收进度、完成率，以及实际结算出去的钱。'
          }
        />
      </AnimatedContent>

      {/* 身份条：角色 + 额度 + 会员。余额不在这（去 /wallet） */}
      <AnimatedContent distance={20} duration={0.5} delay={0.05}>
        <div className="mt-5 flex flex-wrap items-center gap-2 text-xs">
          {role && <span className={CHIP}>身份 · {ROLE_LABELS[role] ?? role}</span>}
          {me && <span className={CHIP}>可用额度 {me.remaining} 次</span>}
          {me?.isMember && me.expiresAt && (
            <span className={CHIP}>会员到期 {me.expiresAt.slice(0, 10)}</span>
          )}
          <button
            type="button"
            onClick={() => navigate('/wallet')}
            className="inline-flex cursor-pointer items-center gap-1 rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:text-foreground"
          >
            <Wallet className="size-3.5" />
            我的钱包
          </button>
        </div>
      </AnimatedContent>

      {loading || !stats ? (
        <div className={cn(CARD_BASE, 'mt-6 flex items-center gap-2 p-8 text-sm text-muted-foreground')}>
          <LoaderCircle className="size-4 animate-spin" /> 正在统计…
        </div>
      ) : isNewbie ? (
        <AnimatedContent distance={24} duration={0.55} delay={0.08}>
          <div className={cn(CARD_BASE, 'mt-6 flex flex-col items-center gap-4 px-6 py-14 text-center')}>
            <ChartColumn className="size-8 text-muted-foreground" />
            <div>
              <p className="font-medium">还没有数据</p>
              <p className="mt-1 max-w-[42ch] text-sm text-muted-foreground">
                发一个需求，或者去大厅接一单，这里就会出现你的完成率与累计金额。
              </p>
            </div>
            <div className="flex flex-wrap justify-center gap-3">
              <GlassButton onClick={() => navigate('/tickets/create')} className="px-5 py-2 text-sm">
                发布需求
              </GlassButton>
              <button
                type="button"
                onClick={() => navigate('/tickets')}
                className="cursor-pointer rounded-full border border-border px-5 py-2 text-sm transition-colors hover:bg-accent"
              >
                去大厅看看
              </button>
            </div>
          </div>
        </AnimatedContent>
      ) : (
        <>
          {/* 两侧对称的四个数字 */}
          <AnimatedContent distance={24} duration={0.55} stagger={0.06} delay={0.08}>
            <div className="mt-6 grid gap-4 lg:grid-cols-2">
              <SideCard
                title="我发布的"
                subtitle="发布者视角"
                emphasize={!isHandler}
                items={[
                  { label: '总发布', value: stats.createdTotal, hint: '不限状态' },
                  { label: '进行中', value: stats.activeAsCreator, hint: '含待验收、被驳回' },
                  { label: '待我验收', value: stats.pendingApprovalAsCreator, hint: '需要你点验收' },
                  { label: '已完成', value: stats.completedAsCreator, hint: '已结算' }
                ]}
                onGo={() => navigate('/tickets')}
                goText="去大厅"
              />

              <SideCard
                title="我接的单"
                subtitle="接单者视角"
                emphasize={isHandler}
                items={[
                  { label: '总接单', value: stats.handledTotal, hint: '不限状态' },
                  { label: '开发中', value: stats.accepted, hint: '还没提交交付' },
                  { label: '待对方验收', value: stats.pendingApproval, hint: '等发布者确认' },
                  { label: '已完成', value: stats.completed, hint: '已结算到账' }
                ]}
                onGo={() => navigate('/tickets')}
                goText="去接单"
              />
            </div>
          </AnimatedContent>

          {/* 完成情况 */}
          <AnimatedContent distance={24} duration={0.55} delay={0.14}>
            <section className={cn(CARD_BASE, 'mt-6 p-6')}>
              <div className="flex items-center gap-2">
                <ChartColumn className="size-4 text-muted-foreground" />
                <h2 className="text-sm font-medium">完成情况</h2>
              </div>

              <div className="mt-5 space-y-5">
                <RateBar
                  label="接单完成率"
                  rate={handlerRate}
                  detail={`${stats.completed} / ${stats.handledTotal} 张`}
                />
                <RateBar
                  label="发布完成率"
                  rate={creatorRate}
                  detail={`${stats.completedAsCreator} / ${stats.createdTotal} 张`}
                />
                <div className="flex items-center justify-between border-t border-border pt-4 text-sm">
                  <span className="text-muted-foreground">全站待接单</span>
                  <span className="tabular-nums">{stats.open} 张</span>
                </div>
              </div>
            </section>
          </AnimatedContent>

          {/* 金额 */}
          <AnimatedContent distance={24} duration={0.55} delay={0.2}>
            <section className={cn(CARD_BASE, 'mt-6 p-6')}>
              <div className="flex items-center gap-2">
                <Coins className="size-4 text-muted-foreground" />
                <h2 className="text-sm font-medium">累计金额</h2>
              </div>

              <div className="mt-5 grid gap-4 sm:grid-cols-2">
                <MoneyTile
                  label="累计收入"
                  value={stats.settledIncome}
                  hint="我接的单验收通过后到账的合计"
                  tone="income"
                />
                <MoneyTile
                  label="累计支出"
                  value={stats.settledPayout}
                  hint="我发布的单结算给接单者的合计"
                  tone="payout"
                />
              </div>

              <p className="mt-4 text-xs leading-5 text-muted-foreground">
                只统计<strong className="font-medium text-foreground">真实结算</strong>过的金额，已存入钱包余额，可用于发布工单（
                <strong className="font-medium text-foreground">不可提现</strong>）。
                资金功能上线前创建的工单不计入 —— 那些单确实没有资金流转。
              </p>
            </section>
          </AnimatedContent>
        </>
      )}
    </div>
  )
}

function SideCard({
  title,
  subtitle,
  emphasize,
  items,
  onGo,
  goText
}: {
  title: string
  subtitle: string
  emphasize?: boolean
  items: { label: string; value: number; hint: string }[]
  onGo: () => void
  goText: string
}) {
  return (
    <section className={cn(CARD_BASE, 'p-6', emphasize && 'border-foreground/20')}>
      <div className="flex items-baseline justify-between">
        <div className="flex items-baseline gap-2">
          <h2 className="text-sm font-medium">{title}</h2>
          <span className="text-[11px] text-muted-foreground">{subtitle}</span>
        </div>
        <button
          type="button"
          onClick={onGo}
          className="cursor-pointer text-xs text-muted-foreground transition-colors hover:text-foreground"
        >
          {goText} →
        </button>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {items.map((it) => (
          <div key={it.label} className="rounded-xl border border-border bg-background px-3 py-3">
            <div className="text-xl font-semibold tabular-nums">{it.value}</div>
            <div className="mt-0.5 text-[11px] text-foreground/80">{it.label}</div>
            <div className="mt-0.5 text-[10px] text-muted-foreground">{it.hint}</div>
          </div>
        ))}
      </div>
    </section>
  )
}

/** 完成率条。`rate === null` 表示分母为 0，显示 — 而不是 0% */
function RateBar({ label, rate, detail }: { label: string; rate: number | null; detail: string }) {
  return (
    <div>
      <div className="flex items-baseline justify-between text-sm">
        <span>{label}</span>
        <span className="flex items-baseline gap-2">
          <span className="text-xs text-muted-foreground">{detail}</span>
          <span className="w-10 text-right font-semibold tabular-nums">
            {rate == null ? '—' : `${rate}%`}
          </span>
        </span>
      </div>
      <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-foreground/70 transition-[width] duration-700 ease-out"
          style={{ width: `${rate ?? 0}%` }}
        />
      </div>
    </div>
  )
}

function MoneyTile({
  label,
  value,
  hint,
  tone
}: {
  label: string
  value: number
  hint: string
  tone: 'income' | 'payout'
}) {
  return (
    <div className="rounded-xl border border-border bg-background px-4 py-4">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div
        className={cn(
          'mt-1 text-2xl font-semibold tabular-nums',
          tone === 'income' ? 'text-emerald-600' : 'text-foreground'
        )}
      >
        {formatAmount(value)}
      </div>
      <div className="mt-1 text-[11px] text-muted-foreground">{hint}</div>
    </div>
  )
}
