import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { cn } from 'cn'

import { ticketApi } from '@/api/ticket'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { AiRailCard } from '@/components/ai/AiRailCard'
import { AiWorkbench } from '@/components/ai/AiWorkbench'
import type { AiTab } from '@/components/ai/AiWorkbench'
import StatusTag, { PriorityTag } from '@/components/feedback/StatusTag'
import {
  ArrowLeft,
  Check,
  CircleAlert,
  CircleCheck,
  Clock,
  FileText,
  MessageCircle,
  Star,
  TriangleAlert,
  Upload,
  WandSparkles
} from '@/components/icons'
import { CompleteDeliveryDialog } from '@/components/ticket/CompleteDeliveryDialog'
import { PickupConfirmMenu } from '@/components/ticket/PickupConfirmMenu'
import { ReviewDeliveryDialog } from '@/components/ticket/ReviewDeliveryDialog'
import { Button } from '@/components/ui/button'
import { GlassLayer } from '@/components/ui/glass-button'
import { Skeleton } from '@/components/ui/skeleton'
import { UserAvatar } from '@/components/user/UserAvatar'
import { useUserAvatars } from '@/hooks/useUserAvatars'
import { useTicketAi } from '@/hooks/useTicketAi'
import type { TicketAi } from '@/hooks/useTicketAi'
import { useAuthStore } from '@/stores/authStore'
import { useTicketStore } from '@/stores/ticketStore'
import type { Priority, TicketDTO } from '@/types'
import { getCategoryLabel, getProjectTypeLabel, getUrgencyLabel } from '@/utils/ticket-labels'

/**
 * 工单详情页。
 *
 * 设计定稿是「多卡片」，不是旧 Vue 那套整页纵向铺开的布局：
 *
 *   页头一张白卡（编号 / 标题 / chips / 元信息）
 *   左栏若干张白卡：AI 速览、需求描述、详细需求、技术栈、进度时间线、交付凭证、驳回理由
 *   右栏：动作卡「现在该谁动」+ 三张玻璃 AI 入口卡
 *   屏幕下方一个共用的 AI 抽屉（分析 / 对话 / PRD 三个 tab）
 *
 * 旧版的「对话记录 / 留言」**整块删掉了**（用户明确说留言不要），所以这个页面
 * 不发任何 comment 请求。编辑页与私聊页仍是占位页，按钮保留、跳过去即可。
 *
 * 几个数据流的约定，改之前先读：
 *
 * 1. **整页按 id 重挂**（`<TicketDetailInner key={id}>`）。`useTicketAi` 里在飞的
 *    AI 请求没有取消机制，换单时如果不重挂，A 单的结果可能在切到 B 单后才返回并
 *    写进 B 的页面。
 * 2. **弹窗成功后不改弹窗组件**：它们内部已经 `triggerRefresh()`，本页订阅那个
 *    计数器，用 `firstRun` ref 跳过首次，之后静默重拉工单 + 额度。
 * 3. **AI 产出对访客与非接单人一律遮住**（摘要、右栏预览、抽屉内容三层都挡）。
 *    后端把 AI 字段随工单一起返回，可见性只能在渲染这一层判。
 */

/** 白卡的基础外观。圆角、边、投影与大厅的工单卡同一套值。 */
const CARD_BASE =
  'rounded-2xl border border-border bg-card shadow-[0_1px_2px_rgba(24,24,27,0.07),0_16px_36px_-18px_rgba(24,24,27,0.3)]'

const CHIP = 'inline-flex items-center rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground'

export default function TicketDetail() {
  const { id } = useParams()
  // key 落在内层：`Number(id)` 在 id 缺失时是 NaN，页面自己会进「编号无效」分支。
  return <TicketDetailInner key={id} ticketId={Number(id)} />
}

function TicketDetailInner({ ticketId }: { ticketId: number }) {
  const navigate = useNavigate()
  const location = useLocation()

  const token = useAuthStore((s) => s.token)
  const myId = useAuthStore((s) => s.userInfo?.id)
  const role = useAuthStore((s) => s.userInfo?.role)
  const refreshTrigger = useTicketStore((s) => s.refreshTrigger)

  const validId = Number.isInteger(ticketId) && ticketId > 0
  const loggedIn = !!token

  /**
   * 大厅点进来的那一瞬，路由 state 里带着列表已有的一份工单（TicketList.openTicket）。
   *
   * 它是"从这张卡展开"过渡的落点：共享元素要求新页面**首帧就有标题卡**可画。
   * 只有编号对得上才采用（浏览器前进/后退会带回历史 state，别拿错单），随后
   * 照常静默重拉 —— 列表那份可能缺详情字段（详细需求、交付凭证等），以服务端为准。
   */
  const seeded = (location.state as { ticket?: TicketDTO } | null)?.ticket
  const seed = validId && seeded?.id === ticketId ? seeded : null

  const [ticket, setTicket] = useState<TicketDTO | null>(seed)
  // 编号本身就不对时不必先闪一帧骨架：直接进错误态。有 seed 时也不闪。
  const [loading, setLoading] = useState(validId && seed == null)
  const [failed, setFailed] = useState(false)

  const [aiOpen, setAiOpen] = useState(false)
  const [aiTab, setAiTab] = useState<AiTab>('analysis')
  const [deliverOpen, setDeliverOpen] = useState(false)
  const [reviewOpen, setReviewOpen] = useState(false)

  /**
   * 请求序号。刷新总线会在短时间内触发多次静默重拉（接单 + 额度），
   * 而响应不保证按发出顺序回来 —— 没有这个守卫，先发后到的旧工单会盖掉新的。
   */
  const seqRef = useRef(0)

  const load = useCallback(
    async (silent = false) => {
      if (!validId) return
      const seq = ++seqRef.current
      if (!silent) {
        setLoading(true)
        setFailed(false)
      }
      try {
        const loaded = await ticketApi.getTicket(ticketId)
        if (seq !== seqRef.current) return
        setTicket(loaded)
        setFailed(false)
      } catch {
        if (seq !== seqRef.current) return
        // 静默刷新失败不动页面：屏幕上那份数据仍是对的，弹窗里的操作已经各自 toast 过。
        // 首屏失败才换成错误态。
        if (!silent) {
          setTicket(null)
          setFailed(true)
        }
      } finally {
        if (seq === seqRef.current) setLoading(false)
      }
    },
    [ticketId, validId]
  )

  /**
   * 首屏拉取。带着 seed 时走**静默**分支：屏幕上已经有列表那份可看，没必要把
   * 标题卡换回骨架（那一下会让共享元素落点的内容闪空）。静默失败也不切错误态 ——
   * 用户刚从大厅点进来，那张单在那儿是能看见的，凭一次超时就判"读不出来"更糟。
   */
  const seededRef = useRef(seed != null)
  useEffect(() => {
    void load(seededRef.current)
  }, [load])

  const ai = useTicketAi({ ticket, loggedIn, onTicketUpdated: setTicket })

  // 刷新总线：接单 / 交付 / 验收成功后静默重拉。首跑跳过，首屏由上面的 effect 负责。
  const firstRefresh = useRef(true)
  useEffect(() => {
    if (firstRefresh.current) {
      firstRefresh.current = false
      return
    }
    void load(true)
    void ai.reloadQuota()
    // load / reloadQuota 的身份会随工单与登录态变化，跟着它们重挂这条订阅没有意义：
    // 这条 effect 只该由计数器驱动。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshTrigger])

  const isCreator = ticket != null && ticket.creatorId === myId
  // `myId != null` 不能省：访客的 myId 是 undefined，而没有接单人的工单 handlerId
  // 也是 undefined，`undefined === undefined` 会让访客看到接单人的全部动作。
  const isHandler = ticket != null && myId != null && ticket.handlerId === myId
  const isArchived = ticket != null && (ticket.status === 'COMPLETED' || ticket.status === 'CLOSED')

  /**
   * 接单的判据与大厅的 `canPickupOn` 是同一套（用户报过"大厅有按钮、点进去没有"）。
   * **刻意不判登录**：访客看得到绿色接单按钮，点下去由 PickupConfirmMenu 弹登录卡。
   */
  const canPickup =
    ticket != null &&
    !ticket.handlerId &&
    ticket.status === 'OPEN' &&
    ticket.creatorId !== myId &&
    role !== 'PUBLISHER'
  const canDeliver = !!ticket && isHandler && (ticket.status === 'ACCEPTED' || ticket.status === 'REJECTED')
  const canReview = !!ticket && isCreator && ticket.status === 'PENDING_APPROVAL'

  const canSeeAi = loggedIn && ai.quota?.isHandler === true

  // ---------------- 非加载态的画面 ----------------

  let content: ReactNode
  if (loading) {
    content = <DetailSkeleton />
  } else if (failed || !ticket) {
    content = (
      <div className="mt-6 flex flex-col items-center gap-3 rounded-3xl border border-border bg-muted/40 px-6 py-20 text-center">
        <span className="flex size-12 items-center justify-center rounded-2xl bg-card text-muted-foreground shadow-sm">
          <CircleAlert size={22} />
        </span>
        <p className="text-sm text-muted-foreground">
          {validId ? '这张工单读不出来，可能已经被删除，或者服务暂时不可用。' : '工单编号无效。'}
        </p>
        {validId && (
          <Button variant="outline" size="sm" onClick={() => void load()}>
            重试
          </Button>
        )}
      </div>
    )
  } else {
    const techs = splitTechs(ticket.techStack)
    const showChat = (isCreator || isHandler) && !isArchived
    const showEdit = isCreator && !isArchived

    const primary = canPickup ? (
      <div className="[&>button]:w-full">
        <PickupConfirmMenu ticket={ticket} loggedIn={loggedIn} />
      </div>
    ) : canDeliver ? (
      <Button className="w-full" onClick={() => setDeliverOpen(true)}>
        <Upload size={15} />
        去完成
      </Button>
    ) : canReview ? (
      <Button className="w-full" onClick={() => setReviewOpen(true)}>
        <CircleCheck size={15} />
        去验收
      </Button>
    ) : null

    const secondary =
      showChat || showEdit ? (
        <>
          {showChat && (
            <Button variant="outline" className="w-full" onClick={() => navigate(`/tickets/${ticket.id}/chat`)}>
              <MessageCircle size={15} />
              进入私聊
            </Button>
          )}
          {showEdit && (
            <Button variant="ghost" className="w-full" onClick={() => navigate(`/tickets/${ticket.id}/edit`)}>
              编辑需求
            </Button>
          )}
        </>
      ) : null

    content = (
      <>
        <TicketHeader ticket={ticket} myId={myId} />

        <div className="mt-6 lg:grid lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start lg:gap-6">
          {/*
            右栏在 DOM 里排在前面：窄屏是单列，动作与 AI 入口应该先出现。
            桌面用 col-start / row-start 把它显式摆到右边，与 DOM 顺序解耦。
          */}
          <aside className="flex flex-col gap-3 lg:sticky lg:top-20 lg:col-start-2 lg:row-start-1">
            <ActionCard ticket={ticket} primary={primary} extra={secondary} />

            {canSeeAi && (
              <p className="px-1 text-xs text-foreground/70">
                AI 台 · 本单还剩 {remainingAiSlots(ai)} 次
              </p>
            )}

            {/* 窄屏三张卡横排（≥640），桌面回到右栏竖排 */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 lg:grid-cols-1">
              <AiRail
                ticket={ticket}
                ai={ai}
                loggedIn={loggedIn}
                canSeeAi={canSeeAi}
                onOpen={(tab) => {
                  setAiTab(tab)
                  setAiOpen(true)
                }}
              />
            </div>
          </aside>

          <main className="mt-6 flex min-w-0 flex-col gap-4 lg:col-start-1 lg:row-start-1 lg:mt-0">
            {canSeeAi && ticket.aiSummary && (
              <SectionCard title="AI 速览" icon={WandSparkles}>
                <p className="text-sm leading-7 whitespace-pre-wrap text-foreground/90">{ticket.aiSummary}</p>
              </SectionCard>
            )}

            <SectionCard title="需求描述">
              <p className="text-sm leading-7 whitespace-pre-wrap text-foreground/90">{ticket.content}</p>
            </SectionCard>

            {ticket.detailedRequirements && (
              <SectionCard title="详细需求">
                <p className="text-sm leading-7 whitespace-pre-wrap text-foreground/90">
                  {ticket.detailedRequirements}
                </p>
              </SectionCard>
            )}

            {techs.length > 0 && (
              <SectionCard title="技术栈">
                <div className="flex flex-wrap gap-1.5">
                  {techs.map((tech) => (
                    <span
                      key={tech}
                      className="rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground"
                    >
                      {tech}
                    </span>
                  ))}
                </div>
              </SectionCard>
            )}

            <TimelineCard ticket={ticket} />

            {ticket.completionProof && <DeliveryProofCard proof={ticket.completionProof} />}

            {ticket.status === 'REJECTED' && ticket.rejectionReason && (
              <section className="flex items-start gap-2 rounded-2xl border border-red-200 bg-red-50 px-5 py-4 text-sm leading-6 text-red-700">
                <TriangleAlert size={16} className="mt-1 shrink-0" />
                <div className="min-w-0">
                  <p className="font-semibold">这张单被驳回了</p>
                  <p className="mt-0.5 whitespace-pre-wrap">{ticket.rejectionReason}</p>
                </div>
              </section>
            )}
          </main>
        </div>

        {/* 交付 / 验收弹窗：成功后由弹窗内部的 triggerRefresh 驱动本页静默重拉 */}
        {deliverOpen && (
          <CompleteDeliveryDialog
            ticket={ticket}
            open
            onOpenChange={(next) => {
              if (!next) setDeliverOpen(false)
            }}
          />
        )}
        {reviewOpen && (
          <ReviewDeliveryDialog
            ticket={ticket}
            open
            onOpenChange={(next) => {
              if (!next) setReviewOpen(false)
            }}
          />
        )}

        {/*
          **常挂载、只切 open**：AI 分析一次要等好几分钟，用户收起抽屉后那次调用
          与已经聊出来的消息都必须活在 hook 里（见 useTicketAi 的说明）。
        */}
        <AiWorkbench
          open={aiOpen}
          tab={aiTab}
          onTabChange={setAiTab}
          onClose={() => setAiOpen(false)}
          ticket={ticket}
          ai={ai}
          loggedIn={loggedIn}
          isCreator={isCreator}
        />
      </>
    )
  }

  return (
    <>
      <div className="mx-auto w-full max-w-[1200px] px-6 py-8">
        {/*
          长条玻璃返回键：嵌在顶栏与标题卡之间的那道空隙里，**左边缘与标题卡对齐**
          —— 所以它跟着内容容器走，不再 `fixed`。原来的圆钮之所以固定，是因为它独自
          贴在视口左上角；改成贴着卡片之后，固定反而会在滚动时压住左栏正文。
          空隙的 32px 来自容器的 py-8，条高 40，标题卡自己带 mt-4，与改之前
          （py-8 + 40px 占位 + mt-4）的总高度一模一样，不会引起跳动。

          玻璃只在有东西可透的地方显形：这里的底是虹彩，往下滚会压到白卡，两种底上
          都靠它自己那圈 inset 阴影 + 箭头立形（和顶栏那个玻璃登录按钮同一套）。
          **别给它加 transform 类的 hover 动效**（scale / translate / rotate）：
          祖先一旦有 transform 就成了 backdrop root，玻璃的折射会被关在它自己盒子里。
        */}
        {/* 包一层 flex：inline 元素会带父级 line-height 的 strut，条高就不再是 40 */}
        <div className="flex">
          <Link
            to="/tickets"
            aria-label="返回需求大厅"
            title="返回需求大厅"
            className="relative inline-flex h-10 items-center gap-2 rounded-full px-4 text-sm font-medium text-foreground transition-colors hover:text-foreground/80 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/50"
          >
            <GlassLayer radius={999} />
            <ArrowLeft size={16} className="relative z-10" />
            <span className="relative z-10">返回需求大厅</span>
          </Link>
        </div>
        {content}
      </div>
    </>
  )
}

// ---------------- 页头 ----------------

function TicketHeader({ ticket, myId }: { ticket: TicketDTO; myId: number | undefined }) {
  // 只关心发布者一个人。走批量接口是为了复用同一套缓存（大厅里刚取过就直接命中）
  const avatars = useUserAvatars([ticket.creatorId])

  return (
    /*
      与大厅卡片同名：View Transition 会把被点开的卡片形变到这块标题卡上。
      页面上只有一处（这里或下面的骨架）会同时挂着这个名字。
    */
    <header
      className={cn(CARD_BASE, 'mt-4 p-6 sm:p-8')}
      style={{ viewTransitionName: 'ticket-card' }}
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-xs tracking-[0.2em] text-muted-foreground uppercase">
          Ticket #{ticket.id}
        </span>
        {ticket.creatorId === myId && (
          <span className="rounded border border-foreground/25 bg-foreground/8 px-1.5 py-0.5 font-mono text-[11px] text-foreground">
            MINE
          </span>
        )}
      </div>

      <h1 className="mt-2 text-2xl leading-snug font-semibold tracking-tight sm:text-3xl">{ticket.title}</h1>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        {ticket.projectType && <span className={CHIP}>{getProjectTypeLabel(ticket.projectType)}</span>}
        {ticket.category && <span className={CHIP}>{getCategoryLabel(ticket.category)}</span>}
        {ticket.priority && <PriorityTag priority={ticket.priority as Priority} />}
        <StatusTag status={ticket.status} />
        {ticket.aiPrepaid && (
          <span className="inline-flex items-center gap-1 rounded-full border border-lime-300 bg-lime-50 px-2.5 py-1 text-xs font-semibold text-lime-700">
            <WandSparkles size={12} />
            AI 已预附
          </span>
        )}
        {ticket.memberPromoted && (
          <span className={CHIP}>
            <Star size={12} className="mr-1" />
            大厅优先曝光
          </span>
        )}
      </div>

      {/*
        dl > div > dt/dd 是 HTML5 允许的写法（dt/dd 必须成组包在同一个 div 里）。
        紧急程度以前从没在任何页面显示过 —— 发布表单一直在收，这一页第一次让它露面。
      */}
      <dl className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-border pt-4 text-sm">
        <MetaItem label="预算" value={ticket.budget != null ? `¥${ticket.budget}` : '面议'} />
        <MetaItem label="期限" value={ticket.deadline ? formatDay(ticket.deadline) : '待定'} />
        {/* 标签写「紧急度」：HIGH 的文案本身就是「紧急」，两个「紧急」并排会读成重复 */}
        <MetaItem label="紧急度" value={ticket.urgency ? getUrgencyLabel(ticket.urgency) : '普通'} />
        <MetaItem
          label="发布"
          value={
            /*
              align-middle 不能省：Avatar 的根节点是 flex，作为 inline-flex 的第一个
              flex item 时基线会落到盒子底边，不加的话头像会把名字往上顶。
            */
            <span className="inline-flex items-center gap-1.5 align-middle">
              <UserAvatar
                avatarUrl={avatars[ticket.creatorId]}
                name={ticket.creatorName}
                className="size-5"
              />
              <span>{ticket.creatorName || `用户 ${ticket.creatorId}`}</span>
            </span>
          }
        />
        {ticket.handlerName && <MetaItem label="接单" value={ticket.handlerName} />}
      </dl>
    </header>
  )
}

function MetaItem({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="font-medium text-foreground tabular-nums">{value}</dd>
    </div>
  )
}

// ---------------- 左栏卡片 ----------------

function SectionCard({
  title,
  icon: Icon,
  children
}: {
  title: string
  icon?: typeof WandSparkles
  children: ReactNode
}) {
  return (
    <section className={cn(CARD_BASE, 'p-6')}>
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        {Icon ? <Icon size={15} className="text-muted-foreground" /> : null}
        {title}
      </h2>
      <div className="mt-3">{children}</div>
    </section>
  )
}

/**
 * 进度时间线：发布 → 接单 → 交付 → 验收。
 *
 * ⚠️ **后端没有「交付时间」字段**。`completedAt` 是验收通过的时间，`resolvedAt`
 * 是更早一代的结束时间，两者都不能标在「交付」那一步上（会把交付显示成验收的
 * 时刻）。所以交付节点只写「已交付」，不写时间。
 */
function TimelineCard({ ticket }: { ticket: TicketDTO }) {
  const delivered =
    !!ticket.completionProof || ticket.status === 'PENDING_APPROVAL' || ticket.status === 'COMPLETED'
  const approvedAt = ticket.completedAt ?? ticket.resolvedAt

  const steps = [
    { label: '发布需求', done: true, time: ticket.createdAt, hint: '' },
    { label: '接单', done: !!ticket.handlerId, time: ticket.acceptedAt ?? '', hint: '等待开发者接单' },
    { label: '交付', done: delivered, time: '', hint: '接单人上传压缩包后完成' },
    {
      label: '验收',
      done: !!approvedAt,
      time: approvedAt ?? '',
      hint: ticket.status === 'PENDING_APPROVAL' ? '等待发布者验收' : '发布者验收通过后完成'
    }
  ]

  return (
    <SectionCard title="进度" icon={Clock}>
      <ol className="mt-1 flex flex-col">
        {steps.map((step, i) => (
          <li key={step.label} className="relative flex gap-3 pb-5 last:pb-0">
            {i < steps.length - 1 && (
              <span aria-hidden="true" className="absolute top-[19px] bottom-0 left-[9px] w-px bg-border" />
            )}
            <span
              className={cn(
                'relative z-10 flex size-[19px] shrink-0 items-center justify-center rounded-full border',
                step.done
                  ? 'border-foreground bg-foreground text-background'
                  : 'border-border bg-card text-muted-foreground'
              )}
            >
              {step.done ? <Check size={11} strokeWidth={3} /> : <span className="size-1.5 rounded-full bg-current" />}
            </span>
            <div className="min-w-0 flex-1">
              <p className={cn('text-sm font-medium', step.done ? 'text-foreground' : 'text-muted-foreground')}>
                {step.label}
              </p>
              {step.done && step.time && (
                <p className="mt-0.5 text-xs text-muted-foreground">{formatDateTime(step.time)}</p>
              )}
              {!step.done && <p className="mt-0.5 text-xs text-muted-foreground">{step.hint}</p>}
            </div>
          </li>
        ))}
      </ol>
    </SectionCard>
  )
}

/**
 * 交付凭证。
 *
 * 只有 `completionProof` 存在才出这张卡。内容可能是链接也可能是纯文本，
 * **绝不走 `dangerouslySetInnerHTML`** —— 它是用户输入，原样按文本渲染。
 */
function DeliveryProofCard({ proof }: { proof: string }) {
  const trimmed = proof.trim()
  const isHttp = /^https?:\/\//i.test(trimmed)

  return (
    <SectionCard title="交付凭证" icon={CircleCheck}>
      <div className="rounded-xl border border-lime-300 bg-lime-50 px-4 py-3 text-sm leading-6 break-all text-lime-800">
        {isHttp ? (
          <a href={trimmed} target="_blank" rel="noopener noreferrer" className="underline underline-offset-4">
            {trimmed}
          </a>
        ) : (
          <p className="whitespace-pre-wrap">{proof}</p>
        )}
      </div>
    </SectionCard>
  )
}

// ---------------- 动作卡 ----------------

function ActionCard({
  ticket,
  primary,
  extra
}: {
  ticket: TicketDTO
  primary: ReactNode
  extra: ReactNode
}) {
  return (
    <section className={cn(CARD_BASE, 'p-5')}>
      <p className="font-mono text-[11px] tracking-[0.2em] text-muted-foreground uppercase">Next step</p>
      <h2 className="mt-1 text-sm font-semibold text-foreground">现在该谁动</h2>
      <p className="mt-2 text-sm leading-6 text-muted-foreground">{situationOf(ticket)}</p>
      {primary ? <div className="mt-4">{primary}</div> : null}
      {extra ? <div className="mt-3 flex flex-col gap-2">{extra}</div> : null}
    </section>
  )
}

/** 没有主按钮时，用一句话说清这张单卡在谁手上。 */
function situationOf(ticket: TicketDTO): string {
  switch (ticket.status) {
    case 'OPEN':
      return '等接单 —— 还没有开发者接下这张需求。'
    case 'ACCEPTED':
      return '接单人在开发中 —— 交付压缩包后会进入待验收。'
    case 'PENDING_APPROVAL':
      return '等发布者验收 —— 交付物已经提交，通过后这张单就完成了。'
    case 'REJECTED':
      return '已退回返工 —— 接单人需要按驳回理由重新交付。'
    case 'COMPLETED':
      return '这张单已经验收完成。'
    case 'CLOSED':
      return '这张单已关闭。'
    default:
      return ''
  }
}

// ---------------- AI 右栏 ----------------

/**
 * 三张玻璃入口卡。
 *
 * `canSeeAi` 为 false 时（访客 / 登录但非接单人）预览一律换成邀请语：
 * `ticket.aiTechSuggestion` / `aiPrd` 是后端随工单一起返回的 AI 产出，
 * 调用方漏判一次就等于把它漏给了访客。
 */
function AiRail({
  ticket,
  ai,
  loggedIn,
  canSeeAi,
  onOpen
}: {
  ticket: TicketDTO
  ai: TicketAi
  loggedIn: boolean
  canSeeAi: boolean
  onOpen: (tab: AiTab) => void
}) {
  const balance = ai.quota?.balance ?? 0
  const isHandler = ai.quota?.isHandler === true
  const lastReply = [...ai.messages].reverse().find((m) => m.role === 'assistant')

  const statusBase = !loggedIn ? '登录后可用' : !ai.quota ? '加载中' : !isHandler ? '接单后可用' : ''

  const analysisStatus = statusBase
    ? statusBase
    : ai.analyzePhase === 'working' || ai.analysisState === 'IN_PROGRESS'
      ? '生成中'
      : ticket.aiTechSuggestion || ticket.aiTaskBreakdown
        ? '已生成'
        : ai.pairPaid
          ? '已解锁'
          : `剩余 ${balance} 次`

  const chatStatus = statusBase
    ? statusBase
    : ai.sending
      ? '回复中'
      : ai.messages.length
        ? `已对话 ${ai.messages.length} 条`
        : ai.chatUnlocked
          ? '已解锁'
          : `剩余 ${balance} 次`

  const prdStatus = statusBase
    ? statusBase
    : ai.prdPhase === 'working'
      ? '生成中'
      : ticket.aiPrd
        ? '已生成'
        : ai.prdState === 'AVAILABLE'
          ? `剩余 ${balance} 次`
          : '已用过'

  const hasAnalysis = !!(ticket.aiTechSuggestion || ticket.aiTaskBreakdown)

  /*
    三张卡的弹出动画（用户 2026-10-20："ai 三个卡片做弹出动画"）。

    每张卡各包一层 AnimatedContent，而不是把三张卡一起包 —— 外层那个 div 会成为
    网格项，三张卡会在它内部竖着堆，把 `sm:grid-cols-3` 整个废掉。分开包正好还能
    用 `delay` 做 0 / 0.08 / 0.16 的逐一弹出（stagger 只管直接子元素，这里用不上）。

    `trigger="mount"`：卡片随详情页数据一起出现，挂载即播。参数是"弹"的写法 ——
    从 0.9 倍、下方 24px 弹到位，`back.out` 会轻微过冲再回落；位移不设为 0 是为了
    让三张卡有一点先后涌出的层次，纯缩放看起来像同时眨了一下。

    包裹层是网格项（会被 stretch 抻到行高），所以卡片的按钮要 `h-full` 填满，
    否则同一行里三张玻璃会各按内容高度参差 —— 与大厅卡片同一个坑。
  */
  const cards = [
    <AiRailCard
      key="analysis"
      title="AI 分析"
      icon={WandSparkles}
      preview={
        canSeeAi
          ? snippet(ticket.aiTechSuggestion || ticket.aiTaskBreakdown) ||
            '让 AI 读一遍需求，产出技术建议、任务拆解与工时预估。'
          : lockedPreview(loggedIn, !!ticket.handlerId, '即可用 AI 给出技术建议与任务拆解。')
      }
      status={analysisStatus}
      ready={canSeeAi && hasAnalysis}
      locked={!canSeeAi}
      onClick={() => onOpen('analysis')}
    />,
    <AiRailCard
      key="chat"
      title="AI 对话"
      icon={MessageCircle}
      preview={
        canSeeAi
          ? (lastReply && snippet(lastReply.content)) ||
            '问点什么吧 —— AI 记得这张单的标题、需求与分析结果。'
          : lockedPreview(loggedIn, !!ticket.handlerId, '就能就这张工单向 AI 提问。')
      }
      status={chatStatus}
      ready={canSeeAi && ai.messages.length > 0}
      locked={!canSeeAi}
      onClick={() => onOpen('chat')}
    />,
    <AiRailCard
      key="prd"
      title="PRD"
      icon={FileText}
      preview={
        canSeeAi
          ? snippet(ticket.aiPrd) || '生成一份可直接交给开发的 PRD（Markdown），最长约 3 分钟。'
          : lockedPreview(loggedIn, !!ticket.handlerId, '即可生成一份可直接交给开发的 PRD。')
      }
      status={prdStatus}
      ready={canSeeAi && !!ticket.aiPrd}
      locked={!canSeeAi}
      onClick={() => onOpen('prd')}
    />
  ]

  return (
    <>
      {cards.map((card, i) => (
        <AnimatedContent
          key={card.key}
          trigger="mount"
          distance={24}
          direction="vertical"
          duration={0.55}
          ease="back.out(1.6)"
          scale={0.9}
          delay={i * 0.08}
          className="h-full"
        >
          {card}
        </AnimatedContent>
      ))}
    </>
  )
}

/** 打码后的预览文案。两句都刻意停在邀请而不是承诺。 */
function lockedPreview(loggedIn: boolean, hasHandler: boolean, what: string) {
  if (!loggedIn) return `登录并接单后，${what}`
  return hasHandler ? '本单已由其他开发者接单，AI 功能只对这张单的接单人开放。' : `接单之后，${what}`
}

/**
 * 本单还能用几次 AI。
 *
 * 「分析与对话」共用 1 格、PRD 1 格，所以最多 2 次；已被用掉的那格不再计入。
 * 这里数的是**这张工单**剩下的次数，不是账户余额（`quota.balance`）——
 * 预附额度的情况下两者本来就不一致。
 */
function remainingAiSlots(ai: TicketAi) {
  const pairLeft = ai.analysisState !== 'USED' || ai.chatState !== 'USED' ? 1 : 0
  const prdLeft = ai.prdState !== 'USED' ? 1 : 0
  return pairLeft + prdLeft
}

// ---------------- 骨架 ----------------

function DetailSkeleton() {
  return (
    <div className="mt-4 flex flex-col gap-4">
      {/* 这块就是标题卡的位置：名字与大厅卡片相同，过渡才有落点（没有 seed 时走这里） */}
      <Skeleton className="h-48 w-full rounded-2xl" style={{ viewTransitionName: 'ticket-card' }} />
      <div className="flex flex-col gap-4 lg:grid lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start lg:gap-6">
        <div className="flex flex-col gap-3 lg:col-start-2 lg:row-start-1">
          <Skeleton className="h-40 w-full rounded-2xl" />
          <Skeleton className="h-28 w-full rounded-2xl" />
        </div>
        <div className="flex flex-col gap-4 lg:col-start-1 lg:row-start-1">
          <Skeleton className="h-44 w-full rounded-2xl" />
          <Skeleton className="h-56 w-full rounded-2xl" />
        </div>
      </div>
    </div>
  )
}

// ---------------- 小工具 ----------------

function splitTechs(techStack?: string) {
  if (!techStack) return []
  return techStack
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 预览用的一行摘要。空白折成单空格，超长截断。 */
function snippet(text?: string, max = 120) {
  if (!text) return ''
  const flat = text.replace(/\s+/g, ' ').trim()
  return flat.length > max ? `${flat.slice(0, max)}…` : flat
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function formatDay(date: string) {
  if (!date) return ''
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return date
  return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())}`
}

function formatDateTime(date?: string) {
  if (!date) return ''
  const d = new Date(date)
  if (Number.isNaN(d.getTime())) return date
  return `${formatDay(date)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
