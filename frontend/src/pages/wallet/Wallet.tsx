import { useCallback, useEffect, useMemo, useState } from 'react'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { CARD_BASE, CHIP, PAGE, PageHeader } from '@/components/layout/PageShell'
import { GlassButton } from '@/components/ui/glass-button'
import { Coins, LoaderCircle, Receipt, TriangleAlert, Wallet } from '@/components/icons'
import { walletApi } from '@/api/wallet'
import { formatAmount, formatDateTime, getOrderStatusLabel, getOrderStatusTone } from '@/utils/order-labels'
import { cn } from '@/lib/utils'
import type { WalletDTO, WalletOrderDTO, WalletTxDTO } from '@/types'

/*
  钱包页 —— 余额、充值、流水。

  ── 三个数字为什么分开放 ──
  「可用」才决定能不能发工单；「托管中」是已经押在在途工单上的钱，看得见但用不了。
  只显示一个"总资产"会让用户困惑："我有 100 为什么发不出去？"

  ── 为什么充值金额只收整数 ──
  后端的金额规则是**整数元**（1 ~ maxRecharge，不支持角/分）。前端这里只是提前拦一道，
  少一次往返；真正说了算的是后端。别为了"顺手支持 0.01"把这条放宽 —— 那会让
  支付金额与库存金额在标度上对不上，回调的金额比对直接失败。

  ── 为什么先下单再跳转 ──
  收银台链接由后端签发（要用应用私钥签名，私钥不能进浏览器）。所以流程是：
  `createOrder` 拿 payUrl → `location.href` 跳过去 → 付完回 /wallet/result 轮询。
  注意链接里带着**下单那一刻**的回调地址，natapp 隧道换域名后旧链接要重新下单。
*/

/** 快捷金额。都是整数，与后端规则一致 */
const QUICK_AMOUNTS = [50, 100, 200, 500]

/** 流水类型的人话。`SETTLE_OUT` 的金额在 frozenDelta 上，见下面的说明 */
const TX_LABELS: Record<WalletTxDTO['type'], string> = {
  RECHARGE: '充值到账',
  FREEZE: '发布工单·托管',
  ESCROW_ADJUST: '调整预算',
  REFUND: '工单关闭·退回',
  SETTLE_OUT: '支付工单款',
  SETTLE_IN: '收到工单款'
}

export default function WalletPage() {
  const [wallet, setWallet] = useState<WalletDTO | null>(null)
  const [orders, setOrders] = useState<WalletOrderDTO[]>([])
  const [txs, setTxs] = useState<WalletTxDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [amount, setAmount] = useState('')
  const [paying, setPaying] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      const [w, o, t] = await Promise.all([
        walletApi.me(),
        walletApi.listOrders(1, 5),
        walletApi.listTransactions(1, 20)
      ])
      setWallet(w)
      setOrders(o.data || [])
      setTxs(t.data || [])
    } catch {
      // 整页进入失败态，而不是显示一堆 0 —— 显示 0 会被当成真实余额
      setLoadFailed(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  /** 金额校验：只接受整数，且在下发的上下限之间 */
  const amountError = useMemo(() => {
    if (amount.trim() === '') return null
    if (!/^\d+$/.test(amount.trim())) return '只能填整数元（不支持角、分）'
    const n = Number(amount)
    if (wallet && n < wallet.minRecharge) return `最少 ${wallet.minRecharge} 元`
    if (wallet && n > wallet.maxRecharge) return `单笔最多 ${wallet.maxRecharge} 元`
    return null
  }, [amount, wallet])

  const canPay = !paying && amount.trim() !== '' && !amountError

  async function goPay() {
    if (!canPay) return
    setPaying(true)
    try {
      const pay = await walletApi.createOrder(Number(amount))
      // 支付宝 pageExecute 正常返回 URL；个别 SDK 版本返回自动提交的 form HTML，
      // 两种都要能处理（与 Membership 页的 goPay 同一套判断）
      const url = pay.payUrl || ''
      if (url.startsWith('<form')) {
        const w = window.open('', '_self')
        if (w) {
          w.document.write(url)
          w.document.close()
        }
      } else {
        window.location.href = url
      }
    } catch {
      // 错误提示已由 client.ts 的拦截器统一弹出（含后端给的 503 配置缺失说明）
      setPaying(false)
    }
  }

  if (loadFailed) {
    return (
      <div className={PAGE}>
        <div className={cn(CARD_BASE, 'flex flex-col items-center gap-4 p-10 text-center')}>
          <TriangleAlert className="size-8 text-muted-foreground" />
          <div>
            <p className="font-medium">钱包加载失败</p>
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
          eyebrow="WALLET"
          title="我的钱包"
          desc="发布工单时预算会先冻结在这里，验收通过后才结算给接单者；工单关闭或删除会退回。"
        />
      </AnimatedContent>

      {/* 三个数字：可用 / 托管 / 合计 */}
      <AnimatedContent distance={24} duration={0.55} stagger={0.06} delay={0.05}>
        <div className="mt-6 grid gap-4 sm:grid-cols-3">
          <BalanceCard
            label="可用余额"
            value={wallet?.availableAmount}
            hint="能用来发布工单"
            icon={<Wallet className="size-4" />}
            emphasis
          />
          <BalanceCard
            label="托管中"
            value={wallet?.frozenAmount}
            hint="已押在在途工单上"
            icon={<LoaderCircle className="size-4" />}
          />
          <BalanceCard
            label="合计"
            value={wallet?.totalAmount}
            hint="可用 + 托管"
            icon={<Coins className="size-4" />}
          />
        </div>
      </AnimatedContent>

      {/* 充值 */}
      <AnimatedContent distance={24} duration={0.55} delay={0.1}>
        <section className={cn(CARD_BASE, 'mt-6 p-6')}>
          <div className="flex items-center gap-2">
            <Coins className="size-4 text-muted-foreground" />
            <h2 className="text-sm font-medium">充值</h2>
            <span className={CHIP}>支付宝沙箱</span>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            {QUICK_AMOUNTS.map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setAmount(String(n))}
                className="cursor-pointer rounded-full border border-border px-3 py-1.5 text-sm transition-colors hover:bg-accent"
              >
                ¥{n}
              </button>
            ))}
          </div>

          <div className="mt-4 flex flex-wrap items-start gap-3">
            <div>
              <div className="flex items-center gap-2">
                <input
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  inputMode="numeric"
                  placeholder="整数元"
                  aria-label="充值金额"
                  className={cn(
                    'h-10 w-40 rounded-lg border bg-transparent px-3 text-sm outline-none transition-colors',
                    amountError ? 'border-destructive' : 'border-input focus-visible:border-ring'
                  )}
                />
                <span className="text-sm text-muted-foreground">元</span>
              </div>
              <p className={cn('mt-1.5 text-xs', amountError ? 'text-destructive' : 'text-muted-foreground')}>
                {amountError || `整数元，${wallet?.minRecharge ?? 1} ~ ${wallet?.maxRecharge ?? 50000} 元`}
              </p>
            </div>

            <GlassButton
              onClick={() => void goPay()}
              disabled={!canPay}
              className="h-10 px-6 text-sm disabled:cursor-not-allowed disabled:opacity-50"
            >
              {paying ? '正在跳转…' : '去支付'}
            </GlassButton>
          </div>

          {/*
            这句话不能省：系统里**没有提现**，钱只能在站内用（发工单）。
            不写清楚，用户充完会找提现入口，找不到就是投诉。
          */}
          <p className="mt-4 flex items-start gap-2 rounded-lg border border-amber-300/60 bg-amber-50/60 p-3 text-xs leading-5 text-amber-800">
            <TriangleAlert className="mt-0.5 size-3.5 shrink-0" />
            <span>
              充值余额仅限站内使用（发布工单托管），
              <strong className="font-semibold">不可提现</strong>。工单完成后款项结算给接单者，
              接单者的余额可用于自己发布工单。
            </span>
          </p>
        </section>
      </AnimatedContent>

      {/* 流水 */}
      <AnimatedContent distance={24} duration={0.55} delay={0.15}>
        <section className={cn(CARD_BASE, 'mt-6 p-6')}>
          <div className="flex items-center gap-2">
            <Receipt className="size-4 text-muted-foreground" />
            <h2 className="text-sm font-medium">资金流水</h2>
            <span className={CHIP}>最近 {txs.length} 条</span>
          </div>

          {loading ? (
            <p className="mt-6 flex items-center gap-2 text-sm text-muted-foreground">
              <LoaderCircle className="size-4 animate-spin" /> 加载中…
            </p>
          ) : txs.length === 0 ? (
            <p className="mt-6 text-sm text-muted-foreground">还没有任何资金记录。充值或发布工单后会出现在这里。</p>
          ) : (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-xs text-muted-foreground">
                    <th className="py-2 pr-4 font-normal">时间</th>
                    <th className="py-2 pr-4 font-normal">类型</th>
                    <th className="py-2 pr-4 font-normal">金额</th>
                    <th className="py-2 pr-4 font-normal">托管变动</th>
                    <th className="py-2 pr-4 font-normal">余额</th>
                    <th className="py-2 font-normal">说明</th>
                  </tr>
                </thead>
                <tbody>
                  {txs.map((tx) => (
                    <tr key={tx.id} className="border-b border-border/60 last:border-0">
                      <td className="py-2.5 pr-4 whitespace-nowrap text-xs text-muted-foreground">
                        {formatDateTime(tx.createdAt)}
                      </td>
                      <td className="py-2.5 pr-4 whitespace-nowrap">{TX_LABELS[tx.type] || tx.type}</td>
                      <td
                        className={cn(
                          'py-2.5 pr-4 whitespace-nowrap tabular-nums',
                          tx.amount > 0 ? 'text-emerald-600' : tx.amount < 0 ? 'text-foreground' : 'text-muted-foreground'
                        )}
                      >
                        {tx.amount === 0 ? '—' : `${tx.amount > 0 ? '+' : ''}${tx.amount.toFixed(2)}`}
                      </td>
                      <td className="py-2.5 pr-4 whitespace-nowrap tabular-nums text-muted-foreground">
                        {tx.frozenDelta === 0
                          ? '—'
                          : `${tx.frozenDelta > 0 ? '+' : ''}${tx.frozenDelta.toFixed(2)}`}
                      </td>
                      <td className="py-2.5 pr-4 whitespace-nowrap tabular-nums">{tx.balanceAfter.toFixed(2)}</td>
                      <td className="py-2.5 text-xs text-muted-foreground">{tx.remark || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </AnimatedContent>

      {/* 充值记录 */}
      {orders.length > 0 && (
        <AnimatedContent distance={24} duration={0.55} delay={0.2}>
          <section className={cn(CARD_BASE, 'mt-6 p-6')}>
            <h2 className="text-sm font-medium">最近充值</h2>
            <ul className="mt-4 space-y-2.5">
              {orders.map((o) => (
                <li key={o.id} className="flex flex-wrap items-center gap-3 text-sm">
                  <span className="tabular-nums">{formatAmount(o.amount)}</span>
                  <span
                    className={cn(
                      'rounded-full border px-2 py-0.5 text-xs',
                      getOrderStatusTone(o.status)
                    )}
                  >
                    {getOrderStatusLabel(o.status)}
                  </span>
                  <span className="font-mono text-xs text-muted-foreground">{o.outTradeNo}</span>
                  <span className="ml-auto text-xs text-muted-foreground">{formatDateTime(o.createdAt)}</span>
                </li>
              ))}
            </ul>
          </section>
        </AnimatedContent>
      )}
    </div>
  )
}

function BalanceCard({
  label,
  value,
  hint,
  icon,
  emphasis
}: {
  label: string
  value?: number
  hint: string
  icon: React.ReactNode
  emphasis?: boolean
}) {
  return (
    <div className={cn(CARD_BASE, 'p-5', emphasis && 'border-foreground/20')}>
      <div className="flex items-center gap-2 text-xs text-muted-foreground">
        {icon}
        {label}
      </div>
      <p className="mt-2 text-2xl font-semibold tabular-nums">
        {value == null ? '—' : formatAmount(value)}
      </p>
      <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
    </div>
  )
}
