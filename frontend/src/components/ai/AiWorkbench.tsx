import { useEffect, useRef } from 'react'
import { cn } from 'cn'

import { AiLockedHint } from '@/components/ai/AiLockedHint'
import { AiWait } from '@/components/ai/AiWait'
import {
  Coins,
  Download,
  FileText,
  Info,
  MessageCircle,
  Send,
  TriangleAlert,
  WandSparkles,
  X
} from '@/components/icons'
import { Button } from '@/components/ui/button'
import { Dialog, DialogOverlay, DialogPopup, DialogPortal, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import type { TicketAi } from '@/hooks/useTicketAi'
import { useUiStore } from '@/stores/uiStore'
import type { TicketDTO } from '@/types'
import { MEMBERSHIP_CREDITS, MEMBERSHIP_DAYS, formatExpireDate } from '@/utils/ai-quota'

/**
 * AI 工作台：**一个**抽屉，顶上一排 tab，装分析 / 对话 / PRD 三件事。
 *
 * ## 为什么是三块共用一个抽屉，而不是三个抽屉
 *
 * 详情页右栏那三张玻璃卡是**入口**，点进来落在同一个工作台上（从哪张卡点开就选中
 * 哪一页）。它们本来就是同一层的三件事："让 AI 在这张工单上干活"。三个独立抽屉等于
 * 三套交互、三份遮罩、三份焦点管理，用户还要记住"我刚才是在哪个抽屉里"。
 *
 * ## 面板为什么是实心白，不是玻璃
 *
 * 玻璃只在**有东西可透**的地方显形（压在虹彩上）；抽屉面板盖着的是一层模糊过的页面，
 * 透出来是浑的，而这里要放的是气泡、代码块和长文 —— 需要最干净的底。所以层级是
 * **玻璃卡 = 入口（机器）、白色实面 = 工作台**。同一个理由，面板里的按钮一律用中性
 * 墨色（`bg-foreground text-background`），不用玻璃。
 *
 * tab 那条也是墨色分段器而不是玻璃：抽屉一开，页面就被模糊层盖住了，玻璃在这里
 * 既没有可折射的东西、又正好落在白面板上，是双重不成立。
 *
 * ## 状态在 hook 里，不在这里
 *
 * 页面调 `useTicketAi()` 拿状态（消息、草稿、三个动作的相位），把整个对象当 `ai`
 * 传进来。**这样抽屉关掉再打开，在飞的那次调用和已经打出来的对话都还在** ——
 * 一次 AI 分析要等好几分钟，用户完全可能先收起来干别的。相应地，页面要把这个组件
 * 一直挂在树上（只切 `open`），不要写成 `{open && <AiWorkbench/>}`。
 */

export type AiTab = 'analysis' | 'chat' | 'prd'

const TABS: { key: AiTab; label: string; icon: typeof WandSparkles }[] = [
  { key: 'analysis', label: 'AI 分析', icon: WandSparkles },
  { key: 'chat', label: 'AI 对话', icon: MessageCircle },
  { key: 'prd', label: 'PRD', icon: FileText }
]

export interface AiWorkbenchProps {
  open: boolean
  /** 当前选中的 tab。**受控** —— 右栏点哪张卡要能直接落在哪一页。 */
  tab: AiTab
  onTabChange: (tab: AiTab) => void
  onClose: () => void
  ticket: TicketDTO | null
  /** `useTicketAi()` 的返回值，由页面持有 */
  ai: TicketAi
  loggedIn: boolean
  /** 我是不是这张单的发布者 —— 「预附额度」那句措辞要分人（他花的钱 vs 白得的） */
  isCreator: boolean
}

/**
 * 给一段"非接单人"的说明补上预附额度这一句。
 *
 * 两类人都需要：发布者本人（他花钱附了，得知道这钱买到了什么）和别的旁观者。
 * **追加而不是替换** —— 原有的"接单后可用 / 已由他人接单"仍要留着，那是主句。
 */
function withPrepaidNote(base: string, ticket: TicketDTO | null, isCreator: boolean): string {
  if (!ticket?.aiPrepaid) return base
  return (
    base +
    (isCreator
      ? '另外，你已为这张单附上 1 次 AI 额度 —— 接单人使用「AI 分析与对话」时不扣他自己的次数。'
      : '另外，这张单的发布者已替他附上 1 次 AI 额度，接单后可免费使用「AI 分析与对话」。')
  )
}

export function AiWorkbench({
  open,
  tab,
  onTabChange,
  onClose,
  ticket,
  ai,
  loggedIn,
  isCreator
}: AiWorkbenchProps) {
  const openAuthCard = useUiStore((s) => s.openAuthCard)
  const listRef = useRef<HTMLDivElement>(null)

  // 消息区滚到底。自己那条与骨架条都要立刻可见 —— 回复要等十几秒，不滚的话用户
  // 在自己的消息都还没进视野的时候就开始等，会以为没发出去。
  useEffect(() => {
    const el = listRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [ai.messages, ai.sending, open, tab])

  if (!ticket) return null

  const tabHasContent = (key: AiTab) => {
    if (key === 'analysis') return !!(ticket.aiTechSuggestion || ticket.aiTaskBreakdown)
    if (key === 'prd') return !!ticket.aiPrd
    return ai.messages.length > 0
  }

  // ---------------- 额度 ----------------

  const balance = ai.quota?.balance ?? 0

  /** 生成一次消耗多少、还剩多少 —— 按钮上方那行小字（只在**这一格还没付过**时显示） */
  const creditHint = () => {
    if (!ai.quota?.isHandler) return ''
    if (ai.analysisState === 'IN_PROGRESS') return '上一次调用还在进行中，请稍候…'
    if (balance <= 0) {
      return `额度不足。开通会员可获得 ${MEMBERSHIP_CREDITS} 次（${MEMBERSHIP_DAYS} 天有效）`
    }
    const expire = formatExpireDate(ai.quota.nextExpireAt)
    return `AI 分析与 AI 对话合并计一次，PRD 另计一次，每张工单共 2 次。本次消耗 1 次（剩余 ${balance} 次${expire ? `，最近 ${expire} 过期` : ''}）`
  }

  /**
   * PRD 那一格的小字。**不要在这里复用 creditHint()** —— 那句是给"分析与对话"那格
   * 写的，说的是"分析与对话合并计一次"，挂在 PRD 按钮下面会读成"这次又要花那一对的
   * 钱"，而且和上面那句"PRD 另计一次"直接打架。
   */
  const prdHint = () => {
    if (balance <= 0) {
      return `额度不足。开通会员可获得 ${MEMBERSHIP_CREDITS} 次（${MEMBERSHIP_DAYS} 天有效）`
    }
    const expire = formatExpireDate(ai.quota?.nextExpireAt)
    return `PRD 单独计一次，本次消耗 1 次（剩余 ${balance} 次${expire ? `，最近 ${expire} 过期` : ''}）。`
  }

  /** 这一格已经付过、但内容还没生成时显示的那句话（只在"分析与对话"这一格用得上） */
  const pairPaidHint =
    'AI 分析与 AI 对话合并计一次，这一次已经付过了，本次生成不再消耗额度。' +
    (ticket.aiPrepaid ? '这 1 次由发布者在发布时预附，不扣你自己的次数。' : '')

  // ---------------- 三个 tab 各自的遮罩与横幅 ----------------
  // 措辞全部照抄旧 Vue 页（TicketDetail.vue 的 analysisMask / chatLocked / chatBanner）。

  const analysisMask = !loggedIn
    ? { title: '登录后可用', detail: 'AI 辅助开发只对登录用户开放，注册即送 5 次额度。', actionText: '去登录' }
    : !ai.quota
      ? { title: '正在加载额度信息…', detail: '', actionText: '' }
      : !ai.quota.isHandler
        ? {
            title: '接单后可用',
            detail: withPrepaidNote(
              ticket.handlerId
                ? '本工单已由其他开发者接单。AI 辅助开发只对这张工单的接单人开放。'
                : '本工单还没有接单人。接单之后，AI 辅助开发就会对你开放。',
              ticket,
              isCreator
            ),
            actionText: ''
          }
        : null

  const chatMask = !loggedIn
    ? {
        title: '登录后可用',
        detail: '登录并接单后，可以用 1 次额度解锁本工单的 AI 对话与 AI 分析。',
        actionText: '去登录'
      }
    : !ai.quota
      ? { title: '正在加载额度信息…', detail: '', actionText: '' }
      : !ai.quota.isHandler
        ? {
            title: '接单后可用',
            detail: withPrepaidNote(
              ticket.handlerId
                ? '本工单已由其他开发者接单。AI 对话只对这张工单的接单人开放。'
                : '本工单还没有接单人。接单之后即可解锁 AI 对话。',
              ticket,
              isCreator
            ),
            actionText: ''
          }
        : ai.chatState === 'IN_PROGRESS'
          ? {
              title: '正在处理中',
              detail: '上一次调用还在进行，完成后本工单的 AI 对话就会解锁。',
              actionText: ''
            }
          : null

  /**
   * 聊天框顶部的状态横幅。只在有必要解释的时候出现，避免变成一个常驻的说明条。
   *   未解锁 → 说清"第一条消息要花 1 次，之后免费"，并且要说清它顺手解锁了 AI 分析
   *   已解锁 → 说清不用再扣，以及（预附时）这 1 次是谁付的
   */
  const chatBanner = (() => {
    if (!loggedIn || !ai.quota?.isHandler) return null
    if (ai.chatState === 'IN_PROGRESS') return null
    if (!ai.chatUnlocked) {
      if (balance <= 0) {
        return {
          tone: 'warn' as const,
          text: `额度不足，无法解锁「AI 分析与对话」。开通会员可获得 ${MEMBERSHIP_CREDITS} 次（${MEMBERSHIP_DAYS} 天有效）。`
        }
      }
      return {
        tone: 'info' as const,
        text: '本工单的「AI 分析与对话」尚未解锁：发送第一条消息将消耗 1 次额度，之后本工单内可无限对话，AI 分析也一并可用（这一对共用这 1 次）。'
      }
    }
    return {
      tone: 'ok' as const,
      text:
        '「AI 分析与对话」已解锁（两者共用这 1 次额度），本工单内可无限提问，不再消耗额度。' +
        (ticket.aiPrepaid ? '这 1 次由发布者在发布时预附，不扣你自己的次数。' : '')
    }
  })()

  // ---------------- 交互 ----------------

  /** 输入框能不能打字。访客、非接单人、额度没到、上一次还在飞 —— 都不行。 */
  const chatInputDisabled =
    !loggedIn ||
    !ai.quota ||
    !ai.quota.isHandler ||
    ai.chatState === 'IN_PROGRESS' ||
    // 已解锁的对话不再扣费，余额为 0 也照常能聊；只有"还没解锁"才需要余额。
    (!ai.chatUnlocked && !ai.canAfford)

  function handleChatKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key !== 'Enter' || e.shiftKey) return
    // 中文输入法组字期间的回车是"选中候选词"，不是"发送"。不挡掉的话，打一半拼音
    // 按回车选词就会把那串拼音发出去 —— 这是中文用户必然会踩到的一次。
    if (e.nativeEvent.isComposing) return
    e.preventDefault()
    void ai.sendMessage(ai.draft)
  }

  // PRD 的按钮状态照抄旧页的 prdButtonDisabled：存量下载永远可用；非接单人不置灰
  // （置灰的按钮什么也不说，让他点了能看到原因 / 登录卡片）。
  const prdDisabled =
    ai.prdPhase === 'working' ||
    (!ai.prdDownloadable &&
      !!ai.quota?.isHandler &&
      (ai.prdState !== 'AVAILABLE' || !ai.canAfford))

  const analysisDisabled = ai.analyzePhase === 'working' || ai.analysisState === 'IN_PROGRESS'

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      {/*
        keepMounted：关掉抽屉时**只把它藏起来，不卸载**。
        否则正在跑的秒表会随卸载归零 —— 用户等了两分钟，收起来再打开会看到 "0.0s"，
        跟内容对不上（内容来自 hook，是活下来的）。Base UI 关掉时会给 popup 加 hidden，
        视觉上仍然是"关了"。副作用是抽屉的 DOM 一直在 body 里 —— 只有一份，无所谓。
      */}
      <DialogPortal keepMounted>        {/*
          模糊遮罩：抽屉一开，底下整页失焦。这里不能用纯 backdrop-blur —— 虹彩太艳，
          糊完还是一层花底子，正文压上去读不清，所以叠一层 40% 的白。
          开合时淡入淡出（Base UI 在这两个阶段挂 data-starting-style / data-ending-style）。
        */}
        <DialogOverlay
          className={cn(
            'bg-background/40 backdrop-blur-md',
            'transition-opacity duration-300',
            'data-[starting-style]:opacity-0 data-[ending-style]:opacity-0'
          )}
        />
        {/*
          从底部弹出（用户 2026-10-20："点击三个卡片后 AI 开发助手界面的弹出动画
          （从底部弹出）"）。`translate-y-full` 是相对自己的高度（78vh），等于从屏幕
          下方整块滑上来；关回去时沿同一条路退场。

          **transition 必须写在 popup 上**：Base UI 是靠 transitionend 判断动画结束
          再收起/隐藏的，没有 transition 时 data-starting-style 会一帧内被摘掉，
          看起来还是"瞬间出现"。`keepMounted` 下每次重新打开都会重播。
        */}
        <DialogPopup
          className={cn(
            'fixed inset-x-0 bottom-0 z-50 mx-auto flex h-[78vh] max-h-[880px] w-full max-w-[1180px] flex-col',
            'overflow-hidden rounded-t-[1.75rem] border border-b-0 border-foreground/12 bg-card',
            'shadow-[0_-1px_2px_rgba(24,24,27,0.06),0_-24px_60px_-24px_rgba(24,24,27,0.35)]',
            /*
              ⚠️ 过渡属性必须是 `translate` 而不是 `transform`：Tailwind v4 的
              `translate-y-full` 输出的是独立的 `translate: 0 100%`（不是
              `transform: translateY(...)`），写 transform 的话位移会瞬间跳到位、
              只有透明度在渐变。实测踩过：初帧 top=900（正确的屏外位置），下一帧直接
              198，看起来像"原地淡入"。
            */
            'transition-[translate,opacity] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]',
            'data-[starting-style]:translate-y-full data-[starting-style]:opacity-0',
            'data-[ending-style]:translate-y-full data-[ending-style]:opacity-0'
          )}
        >
          <DialogTitle className="sr-only">AI 开发助手</DialogTitle>

          {/* ------------------------------------------------ 顶栏：tab */}
          <header className="flex flex-wrap items-center gap-3 border-b border-border px-5 py-3.5 sm:px-7">
            <span className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <WandSparkles size={16} />
              AI 开发助手
            </span>

            <div
              role="tablist"
              aria-label="AI 功能"
              className="flex items-center gap-1 rounded-full border border-foreground/12 bg-muted p-1"
            >
              {TABS.map((t) => {
                const active = t.key === tab
                const Icon = t.icon
                return (
                  <button
                    key={t.key}
                    type="button"
                    role="tab"
                    aria-selected={active}
                    onClick={() => onTabChange(t.key)}
                    className={cn(
                      'inline-flex cursor-pointer items-center gap-2 rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors',
                      'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/50',
                      active
                        ? 'bg-foreground text-background shadow-[0_1px_2px_rgba(24,24,27,0.18)]'
                        : 'text-foreground/70 hover:text-foreground'
                    )}
                  >
                    <Icon size={15} />
                    {t.label}
                    {/* 有内容的小圆点：一眼看出哪几件已经产出过了 */}
                    {tabHasContent(t.key) && (
                      <span
                        aria-hidden="true"
                        className={cn('size-1.5 rounded-full', active ? 'bg-background/80' : 'bg-foreground/45')}
                      />
                    )}
                  </button>
                )
              })}
            </div>

            <span className="ml-auto flex items-center gap-3">
              {ai.quota?.isHandler && (
                <span
                  className="inline-flex items-center gap-1.5 rounded-full border border-foreground/12 bg-muted px-3 py-1 text-xs font-medium text-foreground/70"
                  title="AI 辅助开发额度余额"
                >
                  <Coins size={13} />
                  剩余 {balance} 次
                </span>
              )}
              <button
                type="button"
                onClick={onClose}
                aria-label="关闭 AI 工作台"
                className="inline-flex size-8 cursor-pointer items-center justify-center rounded-full text-foreground/60 transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/50"
              >
                <X size={16} />
              </button>
            </span>
          </header>

          {/* ------------------------------------------------ 内容 */}
          <div className="flex min-h-0 flex-1 flex-col">
            {/* ============================== AI 分析 */}
            {tab === 'analysis' && (
              <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto px-5 py-6 sm:px-7">
                {analysisMask ? (
                  <AiLockedHint
                    {...analysisMask}
                    onAction={analysisMask.actionText ? () => openAuthCard('login') : undefined}
                  />
                ) : (
                  <div className="mx-auto flex max-w-3xl flex-col gap-5">
                    <AiWait phase={ai.analyzePhase} label="AI 正在生成技术建议与任务拆解" />

                    {ai.analyzePhase === 'error' && (
                      <p className="flex items-start gap-2 rounded-xl border border-destructive/25 bg-destructive/5 px-4 py-3 text-sm text-foreground">
                        <TriangleAlert size={16} className="mt-0.5 shrink-0 text-destructive" />
                        <span>AI 分析失败，可能是网络或超时。可以再试一次 —— 这一次没产出内容，额度也还留在原处。</span>
                      </p>
                    )}

                    {ticket.aiTechSuggestion ? (
                      <section className="rounded-2xl border border-border bg-muted/40 p-5">
                        <h3 className="mb-2 text-sm font-semibold text-foreground">技术建议</h3>
                        <div className="text-sm leading-7 whitespace-pre-wrap text-foreground/90">
                          {ticket.aiTechSuggestion}
                        </div>
                      </section>
                    ) : null}

                    {ticket.aiTaskBreakdown ? (
                      <section className="rounded-2xl border border-border bg-muted/40 p-5">
                        <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-foreground">
                          任务拆解
                          {typeof ticket.aiEstimatedHours === 'number' && (
                            <span className="rounded-full bg-foreground/8 px-2 py-0.5 text-xs font-medium text-foreground/70">
                              预估 {ticket.aiEstimatedHours} 小时
                            </span>
                          )}
                        </h3>
                        <div className="text-sm leading-7 whitespace-pre-wrap text-foreground/90">
                          {ticket.aiTaskBreakdown}
                        </div>
                      </section>
                    ) : null}

                    {/* 还没产出 → 生成按钮。两块内容来自**同一次调用**（后端一个槽位），
                        所以只给一个按钮：给两个会让用户以为要花两次。 */}
                    {!ticket.aiTechSuggestion && !ticket.aiTaskBreakdown && (
                      <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-foreground/20 px-6 py-10 text-center">
                        <p className="text-sm text-muted-foreground">
                          一次生成，同时产出「技术建议」与「任务拆解」两块内容。
                        </p>
                        <Button onClick={() => void ai.runAnalysis()} disabled={analysisDisabled}>
                          <WandSparkles size={15} />
                          {ai.analyzePhase === 'working'
                            ? '正在生成…'
                            : ai.pairPaid
                              ? '生成分析与拆解（本次不再消耗额度）'
                              : '生成分析与拆解'}
                        </Button>
                        <p className="max-w-lg text-xs leading-5 text-foreground/60">
                          {ai.pairPaid ? pairPaidHint : creditHint()}
                        </p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* ============================== AI 对话 */}
            {tab === 'chat' && (
              <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto px-5 py-6 sm:px-7">
                {chatMask ? (
                  <AiLockedHint
                    {...chatMask}
                    onAction={chatMask.actionText ? () => openAuthCard('login') : undefined}
                  />
                ) : (
                  <div className="mx-auto flex max-w-3xl flex-col gap-4">
                    {chatBanner && (
                      <p
                        className={cn(
                          'flex items-start gap-2 rounded-xl border px-4 py-3 text-xs leading-5',
                          chatBanner.tone === 'ok' && 'border-foreground/12 bg-muted text-foreground/70',
                          chatBanner.tone === 'info' && 'border-foreground/12 bg-muted text-foreground/70',
                          chatBanner.tone === 'warn' && 'border-destructive/25 bg-destructive/5 text-foreground'
                        )}
                      >
                        <Info size={14} className="mt-0.5 shrink-0" />
                        <span>{chatBanner.text}</span>
                      </p>
                    )}

                    {ai.messages.length === 0 && !ai.sending && (
                      <p className="px-1 py-8 text-center text-sm text-muted-foreground">
                        问点什么吧 —— 它记得这张工单的标题、需求与AI 分析结果。
                      </p>
                    )}

                    {ai.messages.map((m, i) => (
                      <div
                        key={i}
                        className={cn(
                          'max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-7 whitespace-pre-wrap',
                          m.role === 'user'
                            ? 'self-end bg-foreground text-background'
                            : 'self-start border border-border bg-muted/50 text-foreground/90'
                        )}
                      >
                        {m.content}
                      </div>
                    ))}

                    {/* 等回复时的那条气泡。**不是**骨架条：这一等要十几秒到几分钟，
                        骨架条三秒后就成了"页面坏了"。LatticeLoader 带秒表，能一直证明
                        它还活着。 */}
                    {ai.sending && (
                      <div className="self-start rounded-2xl border border-border bg-muted/50 px-4 py-3">
                        <AiWait phase="working" label="AI 正在思考" cellSize={5} fontSize={13} />
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* ============================== PRD */}
            {tab === 'prd' && (
              <div ref={listRef} className="min-h-0 flex-1 overflow-y-auto px-5 py-6 sm:px-7">
                {!loggedIn ? (
                  <AiLockedHint
                    title="登录后可用"
                    detail="登录并接单后，可以用 1 次额度为本工单生成一份 PRD 文档。"
                    actionText="去登录"
                    onAction={() => openAuthCard('login')}
                  />
                ) : !ai.quota ? (
                  <AiLockedHint title="正在加载额度信息…" />
                ) : !ai.quota.isHandler ? (
                  <AiLockedHint
                    title="接单后可用"
                    detail={withPrepaidNote(
                      ticket.handlerId
                        ? '本工单已由其他开发者接单。PRD 生成只对这张工单的接单人开放。'
                        : '本工单还没有接单人。接单之后即可生成本工单的 PRD。',
                      ticket,
                      isCreator
                    )}
                  />
                ) : (
                  <div className="mx-auto flex max-w-3xl flex-col gap-5">
                    <AiWait phase={ai.prdPhase} label="AI 正在生成 PRD" />

                    {ai.prdPhase === 'error' && (
                      <p className="flex items-start gap-2 rounded-xl border border-destructive/25 bg-destructive/5 px-4 py-3 text-sm text-foreground">
                        <TriangleAlert size={16} className="mt-0.5 shrink-0 text-destructive" />
                        <span>PRD 生成失败。这个接口至少要跑三分钟，超时多半是真的太慢了 —— 可以再试一次。</span>
                      </p>
                    )}

                    {ai.prdDownloadable ? (
                      <div className="flex flex-col gap-4 rounded-2xl border border-border bg-muted/40 p-5">
                        <p className="text-sm text-foreground">
                          本工单已生成过 PRD。再点一次是<span className="font-semibold">重新下载同一份文件</span>
                          ，命中后端存量，<span className="font-semibold">不会消耗额度</span>。
                        </p>
                        <div className="max-h-64 overflow-hidden rounded-xl border border-border bg-card p-4 text-xs leading-6 whitespace-pre-wrap text-foreground/70">
                          {ticket.aiPrd}
                        </div>
                        <Button
                          onClick={() => void ai.downloadPrd()}
                          disabled={prdDisabled}
                          className="self-start"
                        >
                          <Download size={15} />
                          下载 PRD
                        </Button>
                      </div>
                    ) : (
                      <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-foreground/20 px-6 py-10 text-center">
                        <p className="text-sm text-muted-foreground">
                          根据这张工单的需求，生成一份可直接交给开发的 PRD 文档（Markdown）。
                        </p>
                        <Button onClick={() => void ai.downloadPrd()} disabled={prdDisabled}>
                          <Download size={15} />
                          生成并下载 PRD
                        </Button>
                        <p className="max-w-lg text-xs leading-5 text-foreground/60">
                          生成一次要等 3 分钟左右（模型同步返回整篇文档），期间请不要关闭这个面板。
                        </p>
                        <p className="max-w-lg text-xs leading-5 text-foreground/60">
                          {ai.prdState !== 'AVAILABLE' ? '「PRD 生成」本工单已经用过了。' : prdHint()}
                        </p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* ============================== 对话输入（只在对话页） */}
            {tab === 'chat' && !chatMask && (
              <footer className="border-t border-border bg-card px-5 py-4 sm:px-7">
                <div className="mx-auto flex max-w-3xl items-end gap-3">
                  <Textarea
                    value={ai.draft}
                    onChange={(e) => ai.setDraft(e.target.value)}
                    onKeyDown={handleChatKeyDown}
                    disabled={chatInputDisabled || ai.sending}
                    rows={2}
                    placeholder={
                      chatInputDisabled
                        ? '登录并接单后可以用 AI 对话'
                        : '问点什么…… 回车发送，Shift + 回车换行'
                    }
                    className="max-h-40 min-h-[56px] flex-1 resize-none"
                  />
                  <Button
                    onClick={() => void ai.sendMessage(ai.draft)}
                    disabled={chatInputDisabled || ai.sending || !ai.draft.trim()}
                    className="h-11 shrink-0"
                  >
                    <Send size={15} />
                    {ai.sending ? '等待回复' : '发送'}
                  </Button>
                </div>
              </footer>
            )}
          </div>
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  )
}
