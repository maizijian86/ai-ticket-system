import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { ArrowLeft, LoaderCircle, TriangleAlert } from '@/components/icons'
import { CARD_BASE, NARROW, PageHeader } from '@/components/layout/PageShell'
import { GlassButton } from '@/components/ui/glass-button'
import {
  TicketFormSections,
  toRequestPayload,
  validateTicketForm,
  type TicketFormValues
} from '@/components/ticket/TicketForm'
import { ticketApi } from '@/api/ticket'
import { useAuthStore } from '@/stores/authStore'
import { useTicketStore } from '@/stores/ticketStore'
import type { TicketDTO } from '@/types'

/*
  编辑需求。

  ── 只有发布者、且工单没完结时能进 ──
  这一页做了两道判断，但都**不是权限边界**：
    · 详情页的「编辑」按钮只在 `isCreator && !isArchived` 时渲染（正常入口）；
    · 这里再拦一次，是为了拦住"直接敲 URL"，别让人对着一个必然提交失败的按钮填半天。
  真正的边界在 `TicketService.updateTicket` 的 requireCreator（403）与
  ARCHIVED_STATUSES 检查（400），前端这两道只是为了体验。

  ── 为什么表单与发布页共用 ──
  后端 UpdateTicketRequest 现在也认 projectType / techStack / budget / deadline /
  detailedRequirements（这一版补上的），字段名与 CreateTicketRequest 逐字一致，
  所以同一个 TicketFormSections 可以直接复用，校验规则也只写一份。
  附带修掉一个老毛病：预算填错了以前只能删了重发，连带丢掉已预附的 AI 额度。
*/

/** 把后端返回的 TicketDTO 铺进表单值。deadline 是 ISO 串，date 输入只吃 YYYY-MM-DD */
function toFormValues(ticket: TicketDTO): TicketFormValues {
  return {
    title: ticket.title ?? '',
    content: ticket.content ?? '',
    detailedRequirements: ticket.detailedRequirements ?? '',
    projectType: ticket.projectType ?? '',
    techStack: ticket.techStack ?? '',
    budget: ticket.budget == null ? '' : String(ticket.budget),
    deadline: ticket.deadline ? ticket.deadline.slice(0, 10) : '',
    urgency: ticket.urgency ?? 'NORMAL'
  }
}

export default function TicketEdit() {
  const { id } = useParams()
  const ticketId = Number(id)
  const navigate = useNavigate()

  const myId = useAuthStore((s) => s.userInfo?.id)
  const triggerRefresh = useTicketStore((s) => s.triggerRefresh)

  const [ticket, setTicket] = useState<TicketDTO | null>(null)
  const [values, setValues] = useState<TicketFormValues | null>(null)
  const [errors, setErrors] = useState<Partial<Record<keyof TicketFormValues, string>>>({})
  const [loading, setLoading] = useState(true)
  const [blocked, setBlocked] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let alive = true
    setLoading(true)
    setBlocked(null)

    ticketApi
      .getTicket(ticketId)
      .then((t) => {
        if (!alive) return
        /*
          纵深防御：与后端同一句文案，走哪条路看到的都是同一句话。

          `myId != null` 这个前置判断是有意的：登录态里 userInfo 是从 localStorage
          恢复的（见 stores/authStore.ts），万一它缺失（换了浏览器、旧会话、手工清过
          存储），这里会把"读不到自己是谁"错当成"你不是发布者"，把发布者挡在自己的
          页面上。读不到就不做这个判断，交给后端的 requireCreator 去 403。
        */
        if (myId != null && t.creatorId !== myId) {
          setBlocked('只有发布者才能编辑该工单')
          return
        }
        if (t.status === 'COMPLETED' || t.status === 'CLOSED') {
          setBlocked('已完结的工单不能编辑')
          return
        }
        setTicket(t)
        setValues(toFormValues(t))
      })
      .catch(() => {
        // 拦截器已经弹过 toast，这里只把页面从骨架里放出来
        if (alive) setBlocked('这张工单打不开，可能是已被删除或你没有查看权限')
      })
      .finally(() => {
        if (alive) setLoading(false)
      })

    return () => {
      alive = false
    }
  }, [ticketId, myId])

  // 被拦住时不要停留在表单上：一秒钟后送回详情页（文案已由 toast 说过一次）
  useEffect(() => {
    if (!blocked) return
    toast.error(blocked)
    const t = window.setTimeout(() => navigate(`/tickets/${ticketId}`, { replace: true }), 1200)
    return () => window.clearTimeout(t)
  }, [blocked, navigate, ticketId])

  /*
    预算锁定的判据与后端一致：**只要有人接单就锁**（不止 ACCEPTED，还有待验收/被驳回）。
    这些状态下 handlerId 都已经有值，所以直接看它。

    为什么不用「状态不是 OPEN」来判：CLOSED 的单本来就不能编辑（ARCHIVED_STATUSES），
    走不到这里；而 REJECTED 的单 handlerId 还在、钱还冻着，同样不该改金额。
  */
  const budgetLocked = Boolean(ticket?.handlerId)

  function setField<K extends keyof TicketFormValues>(key: K, value: TicketFormValues[K]) {
    setValues((v) => (v ? { ...v, [key]: value } : v))
    setErrors((e) => (e[key] ? { ...e, [key]: undefined } : e))
  }

  async function handleSubmit() {
    if (!values) return
    // 编辑页不要求项目类型：存量单可能本来就没有，逼用户补一个才能保存不合理。
    //
    // 这里**不传** availableBalance：编辑页不取钱包余额，而且"加预算差额够不够"
    // 依赖的是此前的冻结额，前端算不准 —— 交给服务端返回 402（带明确差额文案）。
    const found = validateTicketForm(values, { requireProjectType: false })
    setErrors(found)
    if (Object.keys(found).length > 0) {
      toast.error('还有必填项没填完')
      return
    }

    setSubmitting(true)
    try {
      await ticketApi.updateTicket(ticketId, toRequestPayload(values) as Partial<TicketDTO>)
      triggerRefresh()
      toast.success('已保存')
      navigate(`/tickets/${ticketId}`)
    } catch {
      // 拦截器统一提示
    } finally {
      setSubmitting(false)
    }
  }

  // ── 骨架 ──
  if (loading) {
    return (
      <div className={NARROW}>
        <div className="h-4 w-24 animate-pulse rounded bg-muted" />
        <div className="mt-5 h-8 w-40 animate-pulse rounded bg-muted" />
        <div className="mt-6 space-y-4">
          {[0, 1, 2].map((i) => (
            <div key={i} className={`${CARD_BASE} h-40 animate-pulse bg-card/70`} />
          ))}
        </div>
      </div>
    )
  }

  if (blocked || !values || !ticket) {
    return (
      <div className={NARROW}>
        <div className={`${CARD_BASE} flex flex-col items-center gap-3 px-6 py-16 text-center`}>
          <TriangleAlert size={22} className="text-amber-600" />
          <p className="text-sm text-muted-foreground">{blocked ?? '没有可编辑的内容'}</p>
          <button
            type="button"
            onClick={() => navigate(`/tickets/${ticketId}`)}
            className="cursor-pointer text-sm font-medium text-foreground underline-offset-4 hover:underline"
          >
            回到工单详情
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className={NARROW}>
      <AnimatedContent trigger="mount" distance={16} duration={0.4} ease="power2.out" threshold={0}>
        <button
          type="button"
          onClick={() => navigate(`/tickets/${ticketId}`)}
          className="mb-5 inline-flex cursor-pointer items-center gap-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft size={15} />
          返回工单详情
        </button>
      </AnimatedContent>

      <AnimatedContent trigger="mount" distance={20} duration={0.45} ease="power2.out" delay={0.05} threshold={0}>
        <PageHeader
          eyebrow="Edit Requirement"
          title="编辑需求"
          desc={
            ticket.handlerId
              ? '已经有人接单了 —— 改动会被接单者看到，尽量把调整说清楚。'
              : '这张单还没人接，内容可以自由调整。'
          }
        />
      </AnimatedContent>

      <div className="mt-6">
        <TicketFormSections
          values={values}
          errors={errors}
          onChange={setField}
          requireProjectType={false}
          /*
            预算只有"还没人接"时能改：改的是托管金额，加钱要再冻差额（余额不足会整笔
            编辑失败），减钱会把差额退回可用余额。接了单之后金额锁死到结算 ——
            后端也会拒（400「已有接单人，不能再修改预算」），这里禁用只是提前告知。
          */
          budgetDisabled={budgetLocked}
          budgetHint={
            budgetLocked
              ? '已有接单人，预算不可修改（验收通过后按此金额结算）'
              : '整数元，改预算会同步调整托管金额'
          }
        />
      </div>

      <div className="mt-6 flex flex-col-reverse items-stretch gap-2.5 sm:flex-row sm:items-center sm:justify-end">
        <button
          type="button"
          onClick={() => navigate(`/tickets/${ticketId}`)}
          disabled={submitting}
          className="inline-flex h-10 cursor-pointer items-center justify-center rounded-full px-5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
        >
          取消
        </button>

        <GlassButton type="button" onClick={handleSubmit} disabled={submitting} className="h-10 px-6 text-sm">
          {submitting ? <LoaderCircle size={15} className="animate-spin" /> : null}
          {submitting ? '保存中…' : '保存修改'}
        </GlassButton>
      </div>
    </div>
  )
}
