import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { Coins, LoaderCircle, Medal, TriangleAlert } from '@/components/icons'
import { CARD_BASE, PAGE, PageHeader } from '@/components/layout/PageShell'
import { PlanCard } from '@/components/cards/PlanCard'
import { membershipApi } from '@/api/membership'
import { formatExpireDate } from '@/utils/ai-quota'
import {
  formatAmount,
  formatDateTime,
  getOrderStatusLabel,
  getOrderStatusTone
} from '@/utils/order-labels'
import { cn } from '@/lib/utils'
import type { MembershipDTO, MembershipOrderDTO, MembershipPlanDTO } from '@/types'

/*
  开通会员。

  ── 这一页为什么必须存在 ──
  首页第二屏那三张套餐卡的「开通」、顶栏的「开通会员」、以及大厅里额度不足时的引导，
  全都指向 /membership。在它还是占位页的时候，整条付费链路只有"能看价目表"这一半。

  ── 收银台那段为什么不能直接 window.location.href ──
  下单请求是异步的：等 await 回来再开窗口，浏览器**已经**判定这不是用户手势，
  会把它当弹窗拦掉。所以顺序必须是"先同步开一个空窗口，再异步填内容"。
  另外后端返回的 payUrl 有两种形态（正常是 URL；个别 SDK 版本给自动提交的 form HTML），
  两种都要处理 —— 只认 URL 的话会打开一个空白页，用户以为付不了钱。

  ── 付款回来为什么要重新拉一次 ──
  付款在**另一个标签页**里完成，切回本页时 me/orders 都是旧的。不刷新的话用户会看到
  "还是 0 次、还是非会员"，然后可能再买一次。
*/

/** 一页订单数。订单不密集，10 条一页够；翻页只在本页内做，不进 URL */
const ORDER_PAGE_SIZE = 6

export default function Membership() {
  const navigate = useNavigate()

  const [plans, setPlans] = useState<MembershipPlanDTO[] | null>(null)
  const [plansError, setPlansError] = useState(false)
  const [me, setMe] = useState<MembershipDTO | null>(null)
  /** 哪个档位正在下单 —— 用来只让那一张卡的按钮转圈 */
  const [creating, setCreating] = useState<string | null>(null)

  const [orders, setOrders] = useState<MembershipOrderDTO[] | null>(null)
  const [orderPage, setOrderPage] = useState(1)
  const [orderTotal, setOrderTotal] = useState(0)

  const alive = useRef(true)

  const loadPlans = useCallback(async () => {
    setPlansError(false)
    try {
      const list = await membershipApi.plans()
      if (alive.current) setPlans(Array.isArray(list) ? list : [])
    } catch {
      if (alive.current) setPlansError(true)
    }
  }, [])

  const loadMe = useCallback(async () => {
    try {
      const data = await membershipApi.me()
      if (alive.current) setMe(data)
    } catch {
      // 未登录/网关 401 已由拦截器处理；这里保持"未知"状态即可
    }
  }, [])

  const loadOrders = useCallback(async (page: number) => {
    try {
      const res = await membershipApi.listOrders(page, ORDER_PAGE_SIZE)
      if (!alive.current) return
      setOrders(res.data ?? [])
      setOrderTotal(res.total ?? 0)
    } catch {
      if (alive.current) setOrders([])
    }
  }, [])

  useEffect(() => {
    alive.current = true
    void loadPlans()
    void loadMe()
    return () => {
      alive.current = false
    }
  }, [loadPlans, loadMe])

  useEffect(() => {
    void loadOrders(orderPage)
  }, [loadOrders, orderPage])

  // 从支付宝收银台切回来：钱可能已经到账，把状态与订单都重拉一次
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState !== 'visible') return
      void loadMe()
      void loadOrders(orderPage)
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [loadMe, loadOrders, orderPage])

  /**
   * 下单并跳收银台。
   *
   * 注意 `window.open('', '_blank')` 必须在 await **之前** —— 见文件头那段说明。
   */
  async function goPay(plan: MembershipPlanDTO) {
    const w = window.open('', '_blank')
    if (!w) {
      toast.warning('浏览器拦截了新窗口，请允许本站弹出窗口后重试')
      return
    }

    setCreating(plan.planCode)
    try {
      const res = await membershipApi.createOrder(plan.planCode)

      // 两种形态都认：真正的 URL，或者一段自动提交的 form HTML
      if (/^https?:\/\//i.test(res.payUrl)) {
        w.location.href = res.payUrl
      } else {
        w.document.write(res.payUrl)
        w.document.close()
      }
      // 结果页两种参数名都认（支付宝给的是 out_trade_no，这里给驼峰）
      navigate(`/membership/result?outTradeNo=${encodeURIComponent(res.outTradeNo)}`)
    } catch {
      // 下单失败就把占位窗口关掉，别留一个空白页在那儿
      w.close()
      void loadOrders(1)
      setOrderPage(1)
    } finally {
      if (alive.current) setCreating(null)
    }
  }

  const totalPages = Math.max(1, Math.ceil(orderTotal / ORDER_PAGE_SIZE))

  return (
    <div className={PAGE}>
      <AnimatedContent trigger="mount" distance={20} duration={0.5} ease="power2.out" threshold={0}>
        <PageHeader
          eyebrow="Membership"
          title="开通会员"
          desc="AI 分析、PRD 文档、任务拆解与开发对话都消耗额度。额度记在账户上，不按工单分配。"
        />
      </AnimatedContent>

      {/* ── 账户状态条 ── */}
      <AnimatedContent
        trigger="mount"
        distance={24}
        duration={0.5}
        ease="power2.out"
        delay={0.06}
        threshold={0}
      >
        <div className={cn(CARD_BASE, 'mt-6 flex flex-wrap items-center gap-x-8 gap-y-4 px-6 py-5')}>
          <Stat
            icon={<Coins size={15} />}
            label="可用额度"
            value={me ? `${me.remaining}` : '—'}
            unit={me ? '次' : ''}
          />
          <span className="hidden h-9 w-px bg-border sm:block" />
          <Stat
            icon={<Medal size={15} />}
            label="会员状态"
            value={me ? (me.isMember ? (me.planCode ?? '已开通') : '未开通') : '—'}
            tone={me?.isMember ? 'ok' : undefined}
          />
          {me?.isMember && me.expiresAt ? (
            <>
              <span className="hidden h-9 w-px bg-border sm:block" />
              <Stat icon={null} label="到期日" value={formatExpireDate(me.expiresAt)} />
            </>
          ) : null}
          {me?.nextExpireAt ? (
            <>
              <span className="hidden h-9 w-px bg-border sm:block" />
              <Stat icon={null} label="最近一批过期" value={formatExpireDate(me.nextExpireAt)} />
            </>
          ) : null}
        </div>
      </AnimatedContent>

      {/* ── 套餐卡 ── */}
      <div className="mt-4 grid gap-6 md:grid-cols-3">
        {plansError ? (
          <div
            className={cn(
              CARD_BASE,
              'col-span-full flex flex-col items-center gap-3 px-6 py-12 text-center'
            )}
          >
            <TriangleAlert size={20} className="text-amber-600" />
            <p className="text-sm text-muted-foreground">套餐没加载出来 —— 后端可能没起。</p>
            <button
              type="button"
              onClick={() => void loadPlans()}
              className="cursor-pointer rounded-full border border-border bg-card px-4 py-1.5 text-sm font-medium transition-colors hover:bg-accent"
            >
              重试
            </button>
          </div>
        ) : plans === null ? (
          Array.from({ length: 3 }, (_, i) => (
            <div key={i} className={cn(CARD_BASE, 'h-[460px] animate-pulse bg-card/70')} />
          ))
        ) : plans.length === 0 ? (
          <p className={cn(CARD_BASE, 'col-span-full px-6 py-12 text-center text-sm text-muted-foreground')}>
            后端还没有配置任何套餐。
          </p>
        ) : (
          plans.map((plan, i) => (
            <PlanCard
              key={plan.planCode}
              plan={plan}
              featured={plan.planCode === 'PRO'}
              delay={i * 100}
              actionLabel={creating === plan.planCode ? '正在下单…' : me?.isMember ? '再买一期' : '立即开通'}
              onPick={goPay}
            />
          ))
        )}
      </div>

      {/* ── 权益说明 + 我的订单 ── */}
      <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" threshold={0}>
          <section className={cn(CARD_BASE, 'h-full p-6')}>
            <h2 className="text-sm font-semibold">权益说明</h2>
            <ul className="mt-4 flex list-disc flex-col gap-2.5 pl-4 text-sm leading-6 text-muted-foreground marker:text-foreground/30">
              <li>
                额度记在<strong className="text-foreground">账户</strong>上，不按工单分配。
                一张工单最多消耗 2 次：「AI 分析」与「AI 对话」合并计一次（谁先用谁扣），
                「PRD 生成」另计一次。
              </li>
              <li>
                到期后<strong className="text-foreground">没用完的次数跟着作废</strong>，
                不能攒、不能顺延到下一期。
              </li>
              <li>会员未到期时再买一期，新周期从旧的到期日往后接，不会白扔剩余天数。</li>
              <li>购买后即时到账。会员是虚拟权益，已支付的订单不支持退款。</li>
            </ul>
          </section>
        </AnimatedContent>

        <AnimatedContent
          trigger="mount"
          distance={24}
          duration={0.5}
          ease="power2.out"
          delay={0.08}
          threshold={0}
        >
          <section className={cn(CARD_BASE, 'flex h-full flex-col p-6')}>
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold">我的订单</h2>
              {orderTotal > 0 ? (
                <span className="text-xs text-muted-foreground">共 {orderTotal} 笔</span>
              ) : null}
            </div>

            <div className="mt-4 flex-1">
              {orders === null ? (
                <div className="flex h-24 items-center justify-center text-muted-foreground">
                  <LoaderCircle size={16} className="animate-spin" />
                </div>
              ) : orders.length === 0 ? (
                <p className="flex h-24 items-center justify-center text-sm text-muted-foreground">
                  还没有订单
                </p>
              ) : (
                <ul className="divide-y divide-border">
                  {orders.map((o) => (
                    <li key={o.orderId} className="flex items-center gap-3 py-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium">{o.planCode}</span>
                          <span
                            className={cn(
                              'inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium',
                              getOrderStatusTone(o.status)
                            )}
                          >
                            {getOrderStatusLabel(o.status)}
                          </span>
                        </div>
                        <div className="mt-0.5 truncate font-mono text-[11px] text-muted-foreground">
                          {o.outTradeNo}
                        </div>
                        <div className="mt-0.5 text-[11px] text-muted-foreground">
                          {formatDateTime(o.createdAt)}
                        </div>
                      </div>
                      <div className="flex shrink-0 flex-col items-end gap-1">
                        <span className="text-sm font-semibold">{formatAmount(o.amount)}</span>
                        <button
                          type="button"
                          onClick={() =>
                            navigate(`/membership/result?outTradeNo=${encodeURIComponent(o.outTradeNo)}`)
                          }
                          className="cursor-pointer text-[11px] font-medium text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
                        >
                          {o.status === 'PENDING' ? '去支付 / 查看' : '查看'}
                        </button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* 只有一页时不渲染翻页，省得给一个永远点不动的控件 */}
            {totalPages > 1 ? (
              <div className="mt-3 flex items-center justify-between border-t border-border pt-3">
                <button
                  type="button"
                  disabled={orderPage <= 1}
                  onClick={() => setOrderPage((p) => Math.max(1, p - 1))}
                  className="cursor-pointer text-xs font-medium text-muted-foreground transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
                >
                  上一页
                </button>
                <span className="text-xs text-muted-foreground">
                  {orderPage} / {totalPages}
                </span>
                <button
                  type="button"
                  disabled={orderPage >= totalPages}
                  onClick={() => setOrderPage((p) => Math.min(totalPages, p + 1))}
                  className="cursor-pointer text-xs font-medium text-muted-foreground transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
                >
                  下一页
                </button>
              </div>
            ) : null}
          </section>
        </AnimatedContent>
      </div>
    </div>
  )
}

/** 状态条里的一格：图标 + 小标签 + 数值 */
function Stat({
  icon,
  label,
  value,
  unit,
  tone
}: {
  icon: React.ReactNode
  label: string
  value: string
  unit?: string
  tone?: 'ok'
}) {
  return (
    <div className="flex items-center gap-3">
      {icon ? (
        <span className="flex size-8 items-center justify-center rounded-lg border border-border text-muted-foreground">
          {icon}
        </span>
      ) : null}
      <div>
        <div className="text-[11px] text-muted-foreground">{label}</div>
        <div
          className={cn(
            'text-lg font-semibold tracking-tight',
            tone === 'ok' && 'text-emerald-600'
          )}
        >
          {value}
          {unit ? <span className="ml-0.5 text-xs font-normal text-muted-foreground">{unit}</span> : null}
        </div>
      </div>
    </div>
  )
}
