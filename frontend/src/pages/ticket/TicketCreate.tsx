import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { ArrowLeft, LoaderCircle, Send } from '@/components/icons'
import { NARROW, PageHeader } from '@/components/layout/PageShell'
import { confirmDialog } from '@/components/feedback/ConfirmHost'
import { GlassButton } from '@/components/ui/glass-button'
import {
  EMPTY_TICKET_FORM,
  TicketFormSections,
  toRequestPayload,
  validateTicketForm,
  type TicketFormValues
} from '@/components/ticket/TicketForm'
import { ticketApi } from '@/api/ticket'
import { membershipApi } from '@/api/membership'
import { useTicketStore } from '@/stores/ticketStore'
import { buildConsumeConfirmText } from '@/utils/ai-quota'

/*
  发布需求 —— 首页主 CTA 与顶栏「发布需求」的落点。

  ── 为什么这个页面此前是死路 ──
  它曾经只是一句 "under construction"。而顶栏那个「发布需求」按钮只对 PUBLISHER
  显示（见 AppLayout），也就是说**唯一能看到这个入口的角色，点下去撞见的是一张占位页**，
  产品的主漏斗在最上游就断了。

  ── 与旧 Vue 页的差别（不是照搬）──
  布局重排成"四节白卡 + 节头序号"，不是老版那种 label-width:120px 的左标签长表单；
  选择项用胶囊组而不是 el-select；预算与截止日期并排；附额度那一节做成可点整行 + 开关。
  提交前的确认框仍然要，因为附额度是**花钱且不退还**的动作。

  ── 一个刻意的"前端不是权限边界"提醒 ──
  后端**没有**按角色拦创建（只校验已登录），role === 'PUBLISHER' 只是顶栏的显示逻辑。
  所以这里不再加角色判断 —— 直接敲 URL 的 HANDLER 能进来，后端的 403/400 才是边界。
*/

export default function TicketCreate() {
  const navigate = useNavigate()
  const triggerRefresh = useTicketStore((s) => s.triggerRefresh)

  const [values, setValues] = useState<TicketFormValues>(EMPTY_TICKET_FORM)
  const [errors, setErrors] = useState<Partial<Record<keyof TicketFormValues, string>>>({})
  const [submitting, setSubmitting] = useState(false)

  // ── 发布者的 AI 额度余额：勾选框能不能勾由它决定 ──
  const [creditLoaded, setCreditLoaded] = useState(false)
  /** 可用次数；null 表示没读到 —— 读不到就不许勾，免得让用户以为附上了 */
  const [remaining, setRemaining] = useState<number | null>(null)
  const [nextExpireAt, setNextExpireAt] = useState<string | null>(null)
  const [useAiCredit, setUseAiCredit] = useState(false)

  useEffect(() => {
    let alive = true
    membershipApi
      .me()
      .then((me) => {
        if (!alive) return
        setRemaining(me.remaining)
        setNextExpireAt(me.nextExpireAt ?? null)
      })
      .catch(() => {
        // 静默降级：勾选框禁用 + 行内提示，不弹错（进发布页就报错太吵）
        if (alive) setRemaining(null)
      })
      .finally(() => {
        if (alive) setCreditLoaded(true)
      })
    return () => {
      alive = false
    }
  }, [])

  function setField<K extends keyof TicketFormValues>(key: K, value: TicketFormValues[K]) {
    setValues((v) => ({ ...v, [key]: value }))
    // 改过的字段立刻撤掉它的红字，别让报错黏在已经修好的输入框下面
    setErrors((e) => (e[key] ? { ...e, [key]: undefined } : e))
  }

  /**
   * 附额度是花钱的动作，而且**发布即消耗、不退还** —— 这句话必须出现在用户按下之前，
   * 不能只写在页面小字里。文案复用 buildConsumeConfirmText，与详情页那套"消耗几次 /
   * 还剩几次 / 什么时候过期"的口径保持一致。
   */
  async function confirmPrepay() {
    return confirmDialog({
      title: '附上 AI 额度',
      description: buildConsumeConfirmText(
        'AI 分析与对话',
        remaining ?? 0,
        nextExpireAt,
        '这笔额度发布即消耗、不退还（没人接、工单关闭或删除都不退），附上后接单人使用「AI 分析与对话」不再扣他自己的次数。'
      ),
      confirmText: '确认附上',
      cancelText: '再想想'
    })
  }

  async function handleSubmit() {
    const found = validateTicketForm(values, { requireProjectType: true })
    setErrors(found)
    if (Object.keys(found).length > 0) {
      toast.error('还有必填项没填完')
      return
    }

    if (useAiCredit && !(await confirmPrepay())) return

    setSubmitting(true)
    try {
      const ticket = await ticketApi.createTicket({
        ...toRequestPayload(values),
        useAiCredit
      })
      triggerRefresh()
      toast.success(useAiCredit ? '需求已发布，并附上 1 次 AI 额度' : '需求已发布')
      navigate(`/tickets/${ticket.id}`)
    } catch {
      // 错误提示由 axios 拦截器统一弹出（额度不足会是 402 的人话文案）
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className={NARROW}>
      <AnimatedContent trigger="mount" distance={16} duration={0.4} ease="power2.out" threshold={0}>
        <button
          type="button"
          onClick={() => navigate('/tickets')}
          className="mb-5 inline-flex cursor-pointer items-center gap-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft size={15} />
          返回需求大厅
        </button>
      </AnimatedContent>

      <AnimatedContent trigger="mount" distance={20} duration={0.45} ease="power2.out" delay={0.05} threshold={0}>
        <PageHeader
          eyebrow="Publish Requirement"
          title="发布需求"
          desc="写清楚要做什么、给多少预算、什么时候要。需求越具体，报价越准，也越容易被合适的接单者看到。"
        />
      </AnimatedContent>

      <div className="mt-6">
        <TicketFormSections
          values={values}
          errors={errors}
          onChange={setField}
          aiCredit={{
            checked: useAiCredit,
            onCheckedChange: setUseAiCredit,
            loaded: creditLoaded,
            remaining,
            nextExpireAt
          }}
        />
      </div>

      {/* 动作区：贴在最后一张卡下面，不固定在视口底部 —— 表单不长，固定条反而挡内容 */}
      <div className="mt-6 flex flex-col-reverse items-stretch gap-2.5 sm:flex-row sm:items-center sm:justify-end">
        <button
          type="button"
          onClick={() => navigate('/tickets')}
          disabled={submitting}
          className="inline-flex h-10 cursor-pointer items-center justify-center rounded-full px-5 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
        >
          取消
        </button>

        <GlassButton
          type="button"
          onClick={handleSubmit}
          disabled={submitting}
          className="h-10 px-6 text-sm"
        >
          {submitting ? <LoaderCircle size={15} className="animate-spin" /> : <Send size={15} />}
          {submitting ? '发布中…' : '发布需求'}
        </GlassButton>
      </div>

      <p className="mt-4 text-center text-xs text-muted-foreground">
        发布后可以在详情页用 AI 生成技术分析与 PRD；需求内容在有人接单前都能再改。
      </p>
    </div>
  )
}
