import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { CircleCheck, CircleX, Coins, LoaderCircle, TriangleAlert } from '@/components/icons'
import { CARD_BASE, CHIP, NARROW } from '@/components/layout/PageShell'
import { GlassButton } from '@/components/ui/glass-button'
import { membershipApi } from '@/api/membership'
import { formatAmount, formatDateTime, getOrderStatusLabel, getOrderStatusTone } from '@/utils/order-labels'
import { cn } from '@/lib/utils'
import type { MembershipOrderDTO } from '@/types'

/*
  支付结果页 —— 支付宝 `return_url` 的落点（见 ticket-service/application.yml 的
  alipay.return-url），所以它**不是**一个可选页面：用户真金白银付完，浏览器跳回来的
  就是这里。

  ── 这个页面为什么必须轮询 ──
  支付宝的同步跳转（return_url）**不代表钱已经到了**：它只说明用户在收银台点完了，
  而真正改变订单状态的是异步回调（notify_url）。回调可能比跳转晚到几秒，也可能因为
  网络/natapp 隧道抖动而重试。所以「跳回来了」必须当作"待确认"，而不是"成功"。

  ── 为什么切回标签页要重新开始轮询（关键）──
  付款是在**另一个标签页**（支付宝收银台）里完成的。用户付完切回来时，本页那一分钟
  多半已经耗尽、停在"等待支付确认"上。而页面自己不会醒 —— 用户看到的是一张确定
  失败的画面，于是**再付一笔**。实测（沙箱）：一笔支付从点「下一步」到收银台跳走要
  10~15 秒，加上收银台间歇性报错要重试，整个付完往往超过一分钟，这条是常态不是边角。
  见 onVisibilityChange。

  ── 为什么轮询有上限 ──
  上限管的只是"用户一直盯着这一页时，别无止境地转圈"。超时后给「刷新状态」按钮，
  而不是永远转下去。
*/

/** 2 秒 × 30 次 = 1 分钟 */
const POLL_INTERVAL_MS = 2000
const MAX_POLLS = 30

export default function MembershipResult() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  /*
    单号有两个来源，都要认：
      - `out_trade_no` —— **支付宝同步跳转回来时自己加的**，参数名是蛇形；
      - `outTradeNo`   —— 我们下单后 navigate 时带过去的驼峰名，
                          以及用户从「我的订单」点进来的。
    只认一个的话，另一个入口进来会看到"缺少订单号"。
  */
  const outTradeNo = searchParams.get('out_trade_no') || searchParams.get('outTradeNo') || ''

  const [order, setOrder] = useState<MembershipOrderDTO | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [exhausted, setExhausted] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)

  const timer = useRef<ReturnType<typeof setInterval> | null>(null)
  const polls = useRef(0)
  /** 组件是否还挂着 —— 轮询回调里 setState 前必须问一句，否则会写在已卸载的组件上 */
  const alive = useRef(true)

  const stopPolling = useCallback(() => {
    if (timer.current) {
      clearInterval(timer.current)
      timer.current = null
    }
  }, [])

  /**
   * 拉一次订单状态。
   *
   * 用 ref 转一层是因为 startPolling 的 setInterval 回调需要"最新"的这个函数，
   * 而 useCallback 依赖 order 会让 interval 每 2 秒重建一次 —— 那样计时永远重置。
   */
  const fetchOrderRef = useRef<(force?: boolean) => Promise<void>>(async () => {})

  const fetchOrder = useCallback(async (force = false) => {
    if (!outTradeNo) return
    try {
      const data = await membershipApi.getOrder(outTradeNo, force)
      if (!alive.current) return
      setOrder(data)
      setLoadFailed(false)
      // 到终态就停轮询，别让一个已经确定的结果继续每 2 秒打一次接口
      if (data.status !== 'PENDING') stopPolling()
    } catch (e) {
      if (!alive.current) return
      stopPolling()
      // 401（未登录）也走这里：GET 订单要带身份，失败时给的是"查询失败"而不是假成功
      setLoadFailed(true)
      toast.error(e instanceof Error ? e.message : '查询订单失败')
    }
  }, [outTradeNo, stopPolling])

  fetchOrderRef.current = fetchOrder

  const startPolling = useCallback(() => {
    stopPolling()
    polls.current = 0
    timer.current = setInterval(() => {
      polls.current += 1
      if (polls.current > MAX_POLLS) {
        stopPolling()
        if (alive.current) setExhausted(true)
        return
      }
      void fetchOrderRef.current()
    }, POLL_INTERVAL_MS)
  }, [stopPolling])

  // 首次进入：立刻查一次 + 开始轮询
  useEffect(() => {
    alive.current = true
    if (!outTradeNo) return () => { alive.current = false }
    // 首次进入就强制查一次：这一刻用户刚离开收银台，是最该核对的时刻
    void fetchOrder(true)
    startPolling()
    return () => {
      alive.current = false
      stopPolling()
    }
    // outTradeNo 变化（换了一笔单）要重来一轮
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [outTradeNo])

  /**
   * 切回本页时立刻核对一次，并且**重新开始一轮轮询**。
   *
   * 已经是终态就没什么可核对的，别每次切标签都打一次接口。
   */
  useEffect(() => {
    if (!outTradeNo) return
    const onVisibilityChange = () => {
      if (document.visibilityState !== 'visible') return
      if (order && order.status !== 'PENDING') return
      setExhausted(false)
      void fetchOrderRef.current()
      startPolling()
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [outTradeNo, order, startPolling])

  async function manualRefresh() {
    setRefreshing(true)
    await fetchOrderRef.current(true)
    // 还是 PENDING 就再开一轮（而不是让用户再点一次）
    if (order?.status === 'PENDING') {
      setExhausted(false)
      startPolling()
    }
    setRefreshing(false)
  }

  const status = order?.status
  const tone = loadFailed ? 'error' : !order ? 'loading' : status === 'PAID' ? 'success' : status === 'CLOSED' ? 'closed' : 'loading'

  const title = (() => {
    if (!outTradeNo) return '缺少订单号'
    if (loadFailed) return '查询失败'
    if (!order) return '正在确认支付结果…'
    if (status === 'PAID') return '开通成功'
    if (status === 'CLOSED') return '订单已关闭'
    return '等待支付确认'
  })()

  const description = (() => {
    if (!outTradeNo) return '没有带上订单号，无法查询。请到个人主页的「我的订单」里查看。'
    if (loadFailed) return '暂时查不到这笔订单，请稍后重试或到「我的订单」里查看。'
    if (!order) return '正在与服务器核对，请稍候。'
    if (status === 'PAID') {
      return `已为你发放 ${order.planCredits} 次 AI 辅助开发额度，有效期 ${order.planDays} 天。`
    }
    if (status === 'CLOSED') {
      return '这笔订单已关闭（可能是支付超时或你关闭了支付页）。需要的话可以重新下单。'
    }
    return exhausted
      ? '还没收到支付结果。若确实已付款，请点「刷新状态」。'
      : '收到支付结果后本页会自动更新，请不要关闭。'
  })()

  /** 轮询停了、单还没终态时才给手动刷新的入口 —— 否则那按钮是个摆设 */
  const manualRefreshAvailable = Boolean(order) && status === 'PENDING' && exhausted

  const ICON_TONE: Record<string, string> = {
    success: 'border-emerald-300 bg-emerald-50 text-emerald-600',
    error: 'border-red-300 bg-red-50 text-red-600',
    closed: 'border-border bg-muted text-muted-foreground',
    loading: 'border-sky-300 bg-sky-50 text-sky-600'
  }

  return (
    <div className={NARROW}>
      {/*
        入场：mount 触发而不是 scroll —— 这是跳转落地的第一屏，用户不该"滚一下才看见"。
        位移小一点（24）配淡入，避免和浏览器的整页跳转抢戏。
      */}
      <AnimatedContent
        trigger="mount"
        distance={24}
        duration={0.5}
        ease="power2.out"
        animateOpacity
        threshold={0}
      >
        <div className={cn(CARD_BASE, 'px-6 py-10 text-center sm:px-10')}>
          {/*
            状态图标。PAID 用实心勾、CLOSED 用叉、PENDING/加载中用一个转圈 ——
            转圈只在"还没结论"时出现，到了终态还转会让人以为没查完。
          */}
          <div
            className={cn(
              'mx-auto mb-5 flex size-16 items-center justify-center rounded-full border',
              ICON_TONE[tone]
            )}
          >
            {tone === 'success' ? (
              <CircleCheck size={30} />
            ) : tone === 'error' ? (
              <TriangleAlert size={30} />
            ) : tone === 'closed' ? (
              <CircleX size={30} />
            ) : (
              <LoaderCircle size={30} className="animate-spin" />
            )}
          </div>

          <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">{title}</h1>
          <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted-foreground">{description}</p>

          {order ? (
            <dl className="mt-7 divide-y divide-border rounded-xl border border-border bg-background/60 px-5 text-left">
              <DetailRow label="订单号">
                <span className="font-mono text-xs break-all">{order.outTradeNo}</span>
              </DetailRow>
              <DetailRow label="金额">
                <span className="font-semibold">{formatAmount(order.amount)}</span>
              </DetailRow>
              <DetailRow label="状态">
                <span
                  className={cn(
                    'inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium',
                    getOrderStatusTone(order.status)
                  )}
                >
                  {getOrderStatusLabel(order.status)}
                </span>
              </DetailRow>
              {order.paidAt ? <DetailRow label="支付时间">{formatDateTime(order.paidAt)}</DetailRow> : null}
              {order.expiresAt ? (
                <DetailRow label="会员到期">{formatDateTime(order.expiresAt)}</DetailRow>
              ) : null}
              <DetailRow label="本单权益">
                <span className="inline-flex items-center gap-1.5">
                  <Coins size={14} className="text-muted-foreground" />
                  {order.planCredits} 次额度 · {order.planDays} 天
                </span>
              </DetailRow>
            </dl>
          ) : null}

          <div className="mt-7 flex flex-col items-center justify-center gap-2.5 sm:flex-row">
            {manualRefreshAvailable ? (
              <button
                type="button"
                onClick={manualRefresh}
                disabled={refreshing}
                className={cn(
                  CHIP,
                  'h-9 cursor-pointer justify-center px-5 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-60'
                )}
              >
                {refreshing ? <LoaderCircle size={14} className="mr-1.5 animate-spin" /> : null}
                刷新状态
              </button>
            ) : null}

            <GlassButton
              type="button"
              onClick={() => navigate('/profile')}
              className="h-9 w-full px-5 text-sm sm:w-auto"
            >
              查看我的订单
            </GlassButton>

            <button
              type="button"
              onClick={() => navigate('/membership')}
              className="inline-flex h-9 cursor-pointer items-center justify-center rounded-full px-4 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              返回会员页
            </button>
          </div>

          {/*
            说明而不是承诺："1 分钟内到账"这种话在没有通知任务的前提下不成立 ——
            额度是靠回调写的，回调延迟没有上界。所以只描述可观察的事实与可操作的动作。
          */}
          {status === 'PENDING' ? (
            <p className="mx-auto mt-6 max-w-md text-xs leading-5 text-muted-foreground">
              若你已经付款但状态仍停在「待支付」，通常是回执延迟 —— 点「刷新状态」即可；
              超过 15 分钟后刷新会主动向支付宝核对一次。
            </p>
          ) : null}
        </div>
      </AnimatedContent>
    </div>
  )
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-3">
      <dt className="shrink-0 text-xs text-muted-foreground">{label}</dt>
      <dd className="min-w-0 text-right text-sm text-foreground">{children}</dd>
    </div>
  )
}
