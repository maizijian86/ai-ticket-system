import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PlanCard } from '@/components/cards/PlanCard'
import { ArrowRight, Gift, Send, UserRound } from '@/components/icons'
import { GlassButton, SOLID_TILE } from '@/components/ui/glass-button'
import { membershipApi } from '@/api/membership'
import { useAuthStore } from '@/stores/authStore'
import { useUiStore } from '@/stores/uiStore'
import { SIGNUP_PROMO_TITLE } from '@/utils/ai-quota'
import type { MembershipPlanDTO } from '@/types'

/*
  首页 —— 应用打开时落地的这一页。两屏：

    第一屏  主视觉：一句主张、两个按钮，外加访客的赠额度横幅
    第二屏  三张会员套餐卡（数据全部来自后端 /membership/plans）

  第一屏的高度**跟着视口高度走**，不是固定的 640px（2026-10-18 改）：用户要的是
  "16:9 全屏时，折线刚好切在三张套餐卡顶部约 1/5 处"。这是个关于视口高度的要求，
  固定 px 只能在一个屏幕高度上成立 —— 原来的 640px 在 1600×900 上只露出一条边
  （9px），在 1920×1080 上又会露出 2/5。现在把"卡片顶到折线"那一段全部算成常数（见
  FOLD_OFFSET 的推导），首屏高度 = 100svh 减去它，于是任何全屏尺寸下露出的比例都
  一样。第二屏仍是 `min-h` 而不是 `h` —— 三张卡在窄屏会竖着摞起来，写死高度会把
  内容挤出去。

  ── 这里原来有一段动画（2026-10-16 按用户要求撤掉）──
  首屏那两枚按钮会随滚动沿弧线飞进顶栏对应的菜单项。撤的时候配套的东西一起清了：
  AppLayout 上的 `data-nav-slot` 测量锚点、`GlassButton` 为了量尺寸加的 `ref` prop。
  真要做回去，记得踩过的三个坑：`display:none` 的元素 `getBoundingClientRect()`
  返回的是全 0 而不是 null（顶栏在窄屏整条 hidden）；别让"影子"缩成菜单项那个
  盒子（6 个字的按钮缩到 80px 宽，文字会折成三行）；纵向别往上鼓弧（落点就在屏幕
  最上沿，鼓出去中段就整枚飞出视口）。
*/

/**
 * 首屏高度 = 100svh - FOLD_OFFSET（另有 400px 下限，见下）。
 *
 * 卡片顶在文档里的位置 = 57（顶栏）+ heroH + 194，其中 194 是卡片顶到首屏底
 * 的那一截，四段常数：
 *   80   第二屏的 py-20
 *   26   第二屏 justify-center 在 760 里分出来的余量（卡高约 460 时的实测值）
 *   40   标题行高
 *   48   mt-12
 * 要求卡片顶 = 100svh - 92（卡高约 460 的 1/5），于是
 *   heroH = 100svh - 57 - 194 - 92 = 100svh - 343。
 *
 * 下限 400px 只兜底横屏手机这类极端视口；真正卡住的是首屏内容自身的高度
 * （访客约 420px、登录约 332px）。矮屏（1366×768）上 `100svh - 343` 只剩 425px，
 * 所以这两个区间给 `py-16`/`mt-10` 加了 `max-height: 820px` 的收紧档，
 * 否则内容会把首屏撑高、露出不到 1/5。1280×720 仍然做不到（那要首屏压到 377px，
 * 内容放不下），不再追。
 */
const HERO_MIN_HEIGHT = 400
const FOLD_OFFSET = 343
/** 第二屏（会员）的最小高度 */
const PLANS_HEIGHT = 760

export default function Home() {
  const navigate = useNavigate()
  const token = useAuthStore((s) => s.token)
  const role = useAuthStore((s) => s.userInfo?.role)
  const openAuthCard = useUiStore((s) => s.openAuthCard)

  const goHall = () => navigate('/tickets')
  const goCreate = () => navigate('/tickets/new')

  /** 首屏两枚按钮共用的尺寸（跟着内容撑，窄屏下整行铺满） */
  const BUTTON = 'h-11 w-full px-7 text-sm sm:w-auto'
  const SECONDARY =
    'inline-flex cursor-pointer items-center justify-center gap-2 rounded-full border border-border bg-card font-semibold transition-colors hover:bg-accent'

  // ── 会员套餐：数据全来自后端 ────────────────────────────────────────────────
  const [plans, setPlans] = useState<MembershipPlanDTO[] | null>(null)
  const [planError, setPlanError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let alive = true
    setPlanError(false)
    membershipApi
      .plans()
      .then((list) => {
        if (alive) setPlans(Array.isArray(list) ? list : [])
      })
      .catch(() => {
        if (alive) setPlanError(true)
      })
    return () => {
      alive = false
    }
  }, [reloadKey])

  function pickPlan(plan: MembershipPlanDTO) {
    // 访客点「开通」不该弹一个"请先登录"的报错，直接摆出登录卡；选哪一档带到会员页去
    if (!token) {
      openAuthCard('login')
      return
    }
    navigate('/membership', { state: { planCode: plan.planCode } })
  }

  return (
    <div className="flex flex-col">
      {/* ── 第一屏 ─────────────────────────────────────────────────────────── */}
      <section
        style={{ minHeight: `max(${HERO_MIN_HEIGHT}px, calc(100svh - ${FOLD_OFFSET}px))` }}
        className="relative mx-auto flex w-full max-w-3xl flex-col justify-center px-6 py-16 text-center [@media(max-height:820px)]:py-10"
      >
        <span className="font-mono text-xs font-medium tracking-[0.24em] text-muted-foreground uppercase">
          Requirement Hall
        </span>

        <h1 className="mt-5 text-4xl font-semibold tracking-tight text-balance sm:text-5xl lg:text-6xl">
          把需求交给对的人
        </h1>

        <p className="mx-auto mt-5 max-w-xl text-sm leading-7 text-muted-foreground sm:text-base">
          发布需求 · 接单开发 · AI 全程辅助。每张工单都能拿到技术分析、PRD 文档与任务拆解。
        </p>

        <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <GlassButton type="button" onClick={goHall} className={BUTTON}>
            <Send size={16} />
            进入需求大厅
          </GlassButton>

          {/*
            「发布需求」只给 PUBLISHER，与顶栏、大厅统一（用户要求：其他角色都没有
            发布入口）。登录着的非发布者这一格留空 —— 主按钮「进入需求大厅」仍在，
            首屏不会变成一条孤零零的按钮。
          */}
          {role === 'PUBLISHER' ? (
            <button type="button" onClick={goCreate} className={`${BUTTON} ${SECONDARY}`}>
              <ArrowRight size={16} />
              发布需求
            </button>
          ) : !token ? (
            <button type="button" onClick={() => openAuthCard('login')} className={`${BUTTON} ${SECONDARY}`}>
              <UserRound size={16} />
              登录
            </button>
          ) : null}
        </div>

        {!token && (
          <div className="mx-auto mt-10 flex max-w-xl items-center gap-3 rounded-2xl border border-foreground/15 bg-foreground/5 px-4 py-3 text-left [@media(max-height:820px)]:mt-6">
            <span className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${SOLID_TILE}`}>
              <Gift size={16} />
            </span>
            <div className="min-w-0">
              <div className="text-sm font-medium">{SIGNUP_PROMO_TITLE}</div>
              <div className="mt-0.5 text-xs text-muted-foreground">
                AI 技术分析、PRD 文档生成、AI 开发对话，接单后即可使用
              </div>
            </div>
          </div>
        )}
      </section>

      {/* ── 第二屏：会员套餐 ───────────────────────────────────────────────── */}
      <section
        style={{ minHeight: `${PLANS_HEIGHT}px` }}
        className="mx-auto flex w-full max-w-[1200px] flex-col justify-center px-6 py-20"
      >
        <h2 className="text-center text-3xl font-semibold tracking-tight sm:text-4xl">Plan套餐推荐</h2>

        {/*
          卡片的**高度不写死**，交给栅格自己抻平（`items-stretch` 是默认值，三张卡
          自动等高 = 最高的那张的身高）。原来是 `h-[420px]`，结果 Max 那张多一条权益、
          desc 又长一行，底部的「开通」被 `overflow:hidden` 切掉了半截。
          写死也不行：768–1000px 这段每张卡只有 220–300px 宽，desc 折行更多、需要更高。
        */}
        <div className="mt-12 grid gap-6 md:grid-cols-3">
          {planError ? (
            <div className="col-span-full flex flex-col items-center gap-3 rounded-2xl border border-border bg-card/80 px-6 py-12 text-center">
              <p className="text-sm text-muted-foreground">套餐没加载出来 —— 后端可能没起。</p>
              <button
                type="button"
                onClick={() => setReloadKey((k) => k + 1)}
                className="cursor-pointer rounded-full border border-border bg-card px-4 py-1.5 text-sm font-medium transition-colors hover:bg-accent"
              >
                重试
              </button>
            </div>
          ) : plans === null ? (
            // 骨架条：高度往真卡那边凑（真卡是内容撑的，量不出来，只能取个近似值）
            Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="h-[460px] animate-pulse rounded-3xl border border-border bg-card/70" />
            ))
          ) : plans.length === 0 ? (
            <p className="col-span-full py-12 text-center text-sm text-muted-foreground">
              后端还没有配置任何套餐。
            </p>
          ) : (
            plans.map((plan, i) => (
              <PlanCard
                key={plan.planCode}
                plan={plan}
                // 中间那档是推荐：LITE/PRO/MAX 里 PRO 的"每元买到的次数"最长
                featured={plan.planCode === 'PRO'}
                delay={i * 120}
                actionLabel={token ? `开通 ${plan.name}` : '登录后开通'}
                onPick={pickPlan}
              />
            ))
          )}
        </div>
      </section>
    </div>
  )
}
