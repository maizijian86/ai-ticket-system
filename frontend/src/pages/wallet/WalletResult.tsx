import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { CircleCheck, CircleX, LoaderCircle, TriangleAlert } from '@/components/icons'
import { CARD_BASE, CHIP, NARROW } from '@/components/layout/PageShell'
import { GlassButton } from '@/components/ui/glass-button'
import { walletApi } from '@/api/wallet'
import { formatAmount } from '@/utils/order-labels'
import { cn } from '@/lib/utils'
import type { WalletOrderDTO } from '@/types'

/*
  充值结果页 —— 支付宝 `return_url` 的落点（见 AlipayProperties.walletReturnUrl），
  所以它不是可选页面：用户付完，浏览器跳回来就是这里。

  这一页的结构与会员的结果页（MembershipResult）同源，理由也一样，不再重复整段：
    · return_url 只说明「浏览器回来了」，**不说明钱到账** —— 必须轮询后端；
    · 付款是在另一个标签页（收银台）完成的，切回来时要**重新开一轮**轮询，
      否则页面停在一分钟前的"等待确认"上，用户会以为失败再付一笔；
    · 轮询有上限（2s × 30 = 1 分钟），超时给「刷新状态」而不是永远转圈。

  与会员页唯一的不同：到账后的文案说的是「余额已增加」，并且引导去钱包页，
  因为充值的目的通常是马上回去发工单。
*/

const POLL_INTERVAL_MS = 2000
const MAX_POLLS = 30

export default function WalletResultPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  /*
    单号两个来源都要认：
      · `out_trade_no` —— 支付宝同步跳转时自己加的，蛇形；
      · `outTradeNo`   —— 我们下单后 navigate 时带的驼峰名。
    只认一个，另一个入口进来会看到"缺少订单号"。
  */
  const outTradeNo = searchParams.get('out_trade_no') || searchParams.get('outTradeNo') || ''

  const [order, setOrder] = useState<WalletOrderDTO | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [exhausted, setExhausted] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)

  const timer = useRef<ReturnType<typeof setInterval> | null>(null)
  const polls = useRef(0)
  const alive = useRef(true)

  const stopPolling = useCallback(() => {
    if (timer.current) {
      clearInterval(timer.current)
      timer.current = null
    }
  }, [])

  const fetchOrderRef = useRef<(force?: boolean) => Promise<void>>(async () => {})

  const fetchOrder = useCallback(async (force = false) => {
    if (!outTradeNo) return
    try {
      const data = await walletApi.getOrder(outTradeNo, force)
      if (!alive.current) return
      setOrder(data)
      setLoadFailed(false)
      if (data.status !== 'PENDING') stopPolling()
    } catch (e) {
      if (!alive.current) return
      stopPolling()
      setLoadFailed(true)
      toast.error(e instanceof Error ? e.message : '查询充值订单失败')
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [outTradeNo])

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
    if (status === 'PAID') return '充值成功'
    if (status === 'CLOSED') return '充值单已关闭'
    return '等待支付确认'
  })()

  const description = (() => {
    if (!outTradeNo) return '没有带上订单号，无法查询。请到「我的钱包」里查看充值记录。'
    if (loadFailed) return '暂时查不到这笔充值单，请稍后重试或到「我的钱包」里查看。'
    if (!order) return '正在与服务器核对，请稍候。'
    if (status === 'PAID') {
      return `已为你的钱包充值 ${formatAmount(order.amount)}，可以直接用来发布工单。`
    }
    if (status === 'CLOSED') {
      return '这笔充值单已关闭（可能是支付超时或你关闭了支付页）。需要的话可以重新发起充值。'
    }
    return exhausted
      ? '还没收到支付结果。若确实已付款，请点「刷新状态」。'
      : '收到支付结果后本页会自动更新，请不要关闭。'
  })()

  const manualRefreshAvailable = Boolean(order) && status === 'PENDING' && exhausted

  const ICON_TONE: Record<string, string> = {
    success: 'border-emerald-300 bg-emerald-50 text-emerald-600',
    error: 'border-red-300 bg-red-50 text-red-600',
    closed: 'border-border bg-muted text-muted-foreground',
    loading: 'border-sky-300 bg-sky-50 text-sky-600'
  }

  return (
    <div className={NARROW}>
      <AnimatedContent distance={20} duration={0.5} ease="power2.out">
        <h1 className="text-sm tracking-[0.18em] text-muted-foreground uppercase">Wallet</h1>
        <p className="mt-2 text-2xl font-semibold">充值结果</p>
      </AnimatedContent>

      <AnimatedContent distance={24} duration={0.55} delay={0.05}>
        <div className={cn(CARD_BASE, 'mt-6 p-8 text-center')}>
          <div
            className={cn(
              'mx-auto flex size-14 items-center justify-center rounded-full border',
              ICON_TONE[tone]
            )}
          >
            {tone === 'success' ? (
              <CircleCheck className="size-7" />
            ) : tone === 'error' ? (
              <TriangleAlert className="size-7" />
            ) : tone === 'closed' ? (
              <CircleX className="size-7" />
            ) : (
              <LoaderCircle className="size-7 animate-spin" />
            )}
          </div>

          <p className="mt-5 text-lg font-medium">{title}</p>
          <p className="mx-auto mt-2 max-w-[46ch] text-sm leading-6 text-muted-foreground">{description}</p>

          {order && (
            <div className="mt-5 flex flex-wrap items-center justify-center gap-2 text-xs">
              <span className={CHIP}>单号 {order.outTradeNo}</span>
              <span className={CHIP}>{formatAmount(order.amount)}</span>
              {order.tradeNo && <span className={CHIP}>交易号 {order.tradeNo}</span>}
            </div>
          )}

          <div className="mt-7 flex flex-wrap items-center justify-center gap-3">
            {manualRefreshAvailable && (
              <GlassButton
                onClick={() => void manualRefresh()}
                disabled={refreshing}
                className="px-5 py-2 text-sm disabled:opacity-60"
              >
                {refreshing ? '查询中…' : '刷新状态'}
              </GlassButton>
            )}
            <button
              type="button"
              onClick={() => navigate('/wallet')}
              className="cursor-pointer rounded-full border border-border px-5 py-2 text-sm transition-colors hover:bg-accent"
            >
              我的钱包
            </button>
            <button
              type="button"
              onClick={() => navigate('/tickets/create')}
              className="cursor-pointer rounded-full border border-border px-5 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent"
            >
              去发布工单
            </button>
          </div>
        </div>
      </AnimatedContent>
    </div>
  )
}
