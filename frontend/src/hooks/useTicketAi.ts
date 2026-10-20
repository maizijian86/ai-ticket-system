import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { ticketApi } from '@/api/ticket'
import { confirmDialog } from '@/components/feedback/ConfirmHost'
import { useUiStore } from '@/stores/uiStore'
import { AI_FEATURE_LABELS, buildConsumeConfirmText } from '@/utils/ai-quota'
import type { AiFeatureState, AiQuotaStatusDTO, TicketDTO } from '@/types'

/**
 * 一张工单上的 AI 辅助开发：额度状态 + 三个动作（分析 / 对话 / PRD）。
 *
 * 这一段行为全部来自旧 Vue 的 `views/ticket/TicketDetail.vue`（§AI 额度 与
 * §AI 动作的公共前置），逐条搬过来。**状态刻意留在 hook 里而不是抽屉组件里** ——
 * 抽屉关掉时不能把在飞的那次调用连同消息一起丢掉（一次 AI 分析要等好几分钟，
 * 用户完全可能收起来再打开）。所以页面把抽屉一直挂在那儿、只用 open 控制显隐。
 *
 * 三个动作各自一份 phase（idle / working / done / error），UI 直接拿它喂
 * LatticeLoader（见 components/ai/AiWait.tsx）：working 跑波动画，done / error
 * 冻成对勾或叉并停在耗时上。**别用「条件渲染换一个组件」表达完成**，那样看不到
 * 溶解动画、秒表也会从 0 重来。
 */

/** 一次 AI 动作的相位。`working` 之外的三态都只是给 UI 看的展示态。 */
export type AiActionPhase = 'idle' | 'working' | 'done' | 'error'

/** AI 对话的一条气泡。**不是** ChatMessageDTO —— 后者是私聊/工单留言那套带 id 的记录。 */
export interface AiChatMessage {
  role: 'user' | 'assistant'
  content: string
}

/**
 * `done` 之后保留多久再收起来（毫秒）。
 *
 * 对勾要留得住一眼：生成刚完成时内容会同时出现，用户会先看内容再看那句"用时多少"，
 * 一闪而过等于没做。`error` 反过来**不自动消失** —— 红叉和失败原因要一直挂着，
 * 直到用户重试或换 tab，否则一分钟的长等待换来一条 3 秒内蒸发的提示。
 */
const DONE_LINGER_MS = 2500

export interface UseTicketAiOptions {
  ticket: TicketDTO | null
  /** 登录态。访客一律拿不到额度（`/ai-usage` 不在匿名白名单里，发了只会 401）。 */
  loggedIn: boolean
  /** 分析 / PRD 生成成功后后端返回了**整张新工单**，页面要把它换成新的 */
  onTicketUpdated: (ticket: TicketDTO) => void
  /** 生成成功后要顺带刷新的东西（大厅列表的数字等），页面自己决定刷什么 */
  onGenerated?: () => void
}

export function useTicketAi({ ticket, loggedIn, onTicketUpdated, onGenerated }: UseTicketAiOptions) {
  const navigate = useNavigate()

  const [quota, setQuota] = useState<AiQuotaStatusDTO | null>(null)
  const [analyzePhase, setAnalyzePhase] = useState<AiActionPhase>('idle')
  const [prdPhase, setPrdPhase] = useState<AiActionPhase>('idle')
  const [messages, setMessages] = useState<AiChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)

  const ticketId = ticket?.id ?? null

  /**
   * 拉额度。**未登录时不发这个请求** —— `/ai-usage` 不在网关的匿名白名单里，
   * 发出去只会拿到 401，而 401 会被 axios 拦截器当成"登录已过期"弹登录卡片。
   * 访客只是打开一个工单看一眼，不该被这个他没做过的动作袭击。
   *
   * 拿不到也不弹提示：三个功能退化成"用不了"，但详情照常可读，用户对此无能为力。
   */
  const reloadQuota = useCallback(async () => {
    if (!ticketId || !loggedIn) {
      setQuota(null)
      return
    }
    try {
      setQuota(await ticketApi.getAiUsage(ticketId))
    } catch {
      setQuota(null)
    }
  }, [ticketId, loggedIn])

  useEffect(() => {
    void reloadQuota()
  }, [reloadQuota])

  // 换一张工单：额度重拉（上面的 effect），聊天记录与相位必须清空 ——
  // 否则 A 单的对话会挂在 B 单的抽屉里。（<Outlet key> 已经会重挂页面，
  // 这条是给"同页换 id"那种调用方式兜底。）
  const lastTicketId = useRef<number | null>(null)
  useEffect(() => {
    if (lastTicketId.current !== ticketId) {
      lastTicketId.current = ticketId
      setMessages([])
      setDraft('')
      setAnalyzePhase('idle')
      setPrdPhase('idle')
      setSending(false)
    }
  }, [ticketId])

  /**
   * 把 Redis 里的对话历史补回气泡 —— 刷新一下页面不该把聊过的东西全抹掉。
   *
   * **只有接单人才发这个请求**：后端 `/ai-chat/history` 走的是接单人门禁，别人调用
   * 拿 403，而 403 会被 axios 拦截器当场 toast 出来 —— 发布者/访客打开自己的单看
   * 一眼就被弹一句红字，是纯粹的误伤。`quota.isHandler` 是唯一可靠的判据，它在
   * `/ai-usage` 回来之前是 undefined，所以这里天然是"额度到位之后再决定要不要拉"。
   *
   * 读不到一律按"没聊过"处理：后端自己也是这个口径（里头的异常被吞成空列表），
   * 所以这里既不 toast 也不重试 —— "还没有聊过"和"历史读不出来"对用户是同一件事。
   *
   * 拉回来的 role 是 `user` / `ai`（后端 AiChatMessageDTO 的词汇表），要换成气泡用的
   * `user` / `assistant`。两边名字不同是历史原因，**不要**把哪一边改过去对齐：
   * 那个字符串是后端已经序列化出去的契约。
   */
  const handlerKnown = quota?.isHandler ?? false
  useEffect(() => {
    if (!ticketId || !handlerKnown) return
    let alive = true
    ticketApi
      .getAiChatHistory(ticketId)
      .then((history) => {
        if (!alive) return
        // **只在气泡还是空的时候填**：用户可能在这一两秒里已经发出了一条，覆盖式地
        // setMessages 会把他刚打的字和已经出现的回答一起抹掉。
        setMessages((prev) =>
          prev.length
            ? prev
            : history.map(
                (m): AiChatMessage => ({
                  role: m.role === 'user' ? 'user' : 'assistant',
                  content: m.content
                })
              )
        )
      })
      .catch(() => {
        /* 见上：读不到不是错误 */
      })
    return () => {
      alive = false
    }
  }, [ticketId, handlerKnown])

  // done 之后自动收起来（见 DONE_LINGER_MS）。error 停在那儿等人处理。
  useEffect(() => {
    if (analyzePhase !== 'done') return
    const t = setTimeout(() => setAnalyzePhase('idle'), DONE_LINGER_MS)
    return () => clearTimeout(t)
  }, [analyzePhase])
  useEffect(() => {
    if (prdPhase !== 'done') return
    const t = setTimeout(() => setPrdPhase('idle'), DONE_LINGER_MS)
    return () => clearTimeout(t)
  }, [prdPhase])

  // ---------------- 派生状态 ----------------

  /** quota 未就绪时一律按 NOT_HANDLER 处理：宁可少露按钮也不多露。 */
  const analysisState: AiFeatureState = quota?.analysis ?? 'NOT_HANDLER'
  const chatState: AiFeatureState = quota?.chat ?? 'NOT_HANDLER'
  const prdState: AiFeatureState = quota?.prd ?? 'NOT_HANDLER'

  /**
   * 「AI 分析与对话」这一格是否已经付过。后端把同一次消耗同时记在 analysis 与 chat
   * 两个字段上，所以两者必然相等；这里用 `||` 而不是取其一，是免得以后谁只改了一边
   * 就悄悄改变了判定。
   *
   * ⚠️ **USED 不等于"不能用了"**。合并之后它只说明这一格的开销已经结清：两个功能谁
   * 还没产出过内容，谁就能再跑一次、不再扣费。所以它决定的是"要不要弹确认框、还要不要
   * 看余额"，不是"按钮能不能按"。
   */
  const pairPaid = analysisState === 'USED' || chatState === 'USED'
  const chatUnlocked = pairPaid

  /**
   * 余额还够不够付一次。
   *
   * 后端返回的功能状态（AVAILABLE / USED / …）**不含余额维度** —— 余额为 0 时三个
   * 功能仍是 AVAILABLE，要等你按下去了才回 402。所以"余额不足就置灰"只能在前端加，
   * 否则用户看到一个可点的按钮，点完只得到一句错误。
   */
  const canAfford = (quota?.balance ?? 0) > 0

  /** 工单上已有存量 PRD → 重复下载命中后端存量，不扣费，任何状态下都不用拦 */
  const prdDownloadable = !!ticket?.aiPrd

  // ---------------- 动作的公共前置 ----------------

  /**
   * 三个 AI 动作的公共前置。返回 true 表示可以继续走确认框。
   *
   * 这些判断后端**全都再做一遍**，这里只是让用户不必先失败一次才知道原因 ——
   * 前端永远不是权限边界。
   */
  const aiPrecheck = useCallback(
    (state: AiFeatureState, label: string): boolean => {
      if (!loggedIn) {
        useUiStore.getState().openAuthCard('login')
        return false
      }
      if (!quota) {
        toast.warning('额度信息还在加载，请稍候再试')
        return false
      }
      if (!quota.isHandler) {
        toast.warning(
          ticket?.handlerId
            ? '本工单已由其他开发者接单，只有接单人可以使用 AI 功能'
            : '接单后才可以使用 AI 功能'
        )
        return false
      }
      if (state === 'USED') {
        // 合并之后"分析与对话"这半边的 USED 走不到这里 —— 调用方会先看到 pairPaid
        // 而跳过整个前置（那一次不用再花钱）。能撞到这句的实际上只剩 PRD。
        toast.warning(`该工单的「${label}」已经用过了`)
        return false
      }
      if (state === 'IN_PROGRESS') {
        toast.warning('上一次调用还在进行中，请稍候再试')
        return false
      }
      if (quota.balance <= 0) {
        toast.error('AI 辅助开发额度不足')
        // 直接带他去会员页：这里除了"额度不够"没有别的出路，停在原地说一句"额度不足"
        // 然后什么都不做，等于让他自己去猜哪里能充。
        navigate('/membership')
        return false
      }
      return true
    },
    [loggedIn, quota, ticket?.handlerId, navigate]
  )

  /** 消耗前的确认框。返回 false = 用户点了"再想想" */
  const confirmConsume = useCallback(
    async (label: string, isChat: boolean): Promise<boolean> => {
      const balance = quota?.balance ?? 0
      // 分析与对话共用这 1 次，花在哪一边都要把"另一边也解锁了"说清楚 —— 否则用户
      // 事后发现自己"白得了"一件东西，第一反应是怀疑哪里算错了。
      const pairNote =
        label === AI_FEATURE_LABELS.ANALYSIS
          ? '「AI 分析与 AI 对话」共用这 1 次，AI 对话也会一并解锁。'
          : ''
      const description = isChat
        ? `本次将消耗 1 次 AI 辅助开发额度（当前剩余 ${balance} 次）。解锁后本工单内可无限对话，并同时解锁 AI 分析（这一对共用这 1 次），不再扣费。确认解锁？`
        : buildConsumeConfirmText(label, balance, quota?.nextExpireAt, pairNote)
      return confirmDialog({
        title: '消耗额度确认',
        description,
        confirmText: '确认使用',
        cancelText: '再想想'
      })
    },
    [quota]
  )

  // ---------------- 三个动作 ----------------

  /**
   * AI 分析（技术建议 + 任务拆解）。
   *
   * 一次消耗同时产出两个 tab 的内容 —— 后端那边也是一个槽位，所以两个 tab 共用
   * 一个 phase，一个在飞时另一个也禁用，避免用户以为"再点一次能生成另一半"。
   *
   * pairPaid 时这一格已经由同组的另一半（AI 对话）付过了：这次生成免费，不弹确认框，
   * 也不看余额 —— 后端走"不再扣费"那条路。整段跳过前置是安全的：pairPaid 蕴含已登录、
   * 是接单人、quota 已就绪（否则状态不可能是 USED）。
   */
  const runAnalysis = useCallback(async () => {
    if (!ticket || analyzePhase === 'working') return
    if (!pairPaid) {
      if (!aiPrecheck(analysisState, AI_FEATURE_LABELS.ANALYSIS)) return
      if (!(await confirmConsume(AI_FEATURE_LABELS.ANALYSIS, false))) return
    }

    setAnalyzePhase('working')
    try {
      const updated = await ticketApi.analyzeTicket(ticket.id)
      onTicketUpdated(updated)
      toast.success('AI 分析完成')
      setAnalyzePhase('done')
      await reloadQuota()
      onGenerated?.()
    } catch {
      // 业务错误（已用过 / 额度不足 / 非接单人）的提示由 axios 拦截器弹过，
      // 这里只兜网络与超时 —— 否则用户会连着看到两条意思一样的红字。
      setAnalyzePhase('error')
    }
  }, [
    ticket,
    analyzePhase,
    pairPaid,
    analysisState,
    aiPrecheck,
    confirmConsume,
    onTicketUpdated,
    reloadQuota,
    onGenerated
  ])

  /**
   * 生成并下载 PRD。
   *
   * 两种情况**不需要消耗额度**，因此也跳过确认框：工单上已有存量 PRD（后端命中存量
   * 直接返回，不扣费），以及用户点的本来就是"再下一份文件"。
   */
  const downloadPrd = useCallback(async () => {
    if (!ticket || prdPhase === 'working') return

    const needGenerate = !prdDownloadable
    if (needGenerate) {
      if (!aiPrecheck(prdState, AI_FEATURE_LABELS.PRD)) return
      if (!(await confirmConsume(AI_FEATURE_LABELS.PRD, false))) return
    }

    setPrdPhase('working')
    try {
      // 拦截器对 blob 请求直接返回 Blob 本身，这里不能再取 response.data
      // （Blob 没有 .data，取到的是 undefined，下载出来的文件内容就是字符串 "undefined"）。
      const blob = (await ticketApi.downloadPrd(ticket.id)) as unknown as Blob
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `PRD_${ticket.title}.md`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
      toast.success('PRD 下载成功')
      setPrdPhase('done')
      if (needGenerate) {
        // 生成路径要刷新工单（aiPrd 已落库，"去生成"要变成"下载 PRD"）与额度
        onTicketUpdated(await ticketApi.getTicket(ticket.id))
        await reloadQuota()
        onGenerated?.()
      }
    } catch {
      setPrdPhase('error')
    }
  }, [
    ticket,
    prdPhase,
    prdDownloadable,
    prdState,
    aiPrecheck,
    confirmConsume,
    onTicketUpdated,
    reloadQuota,
    onGenerated
  ])

  /**
   * AI 对话。**首条消息消耗 1 次额度，解锁「AI 分析与对话」这一格**（后端语义）：
   * 之后本工单内无限对话，AI 分析也一并可用。
   *
   * 所以确认框只在"这一格还没付过"时弹：已付过还每次弹一次，用户会以为每条消息都在扣费。
   * 反过来，如果同组的 AI 分析先付过了，chatUnlocked 也是 true —— 不需要再花钱，
   * 也就不该弹框，这正是"谁先用谁扣、另一个免费"在前端的落点。
   *
   * 失败时把问题**还回草稿框**：用户刚打的一段字不能就这么没了。
   */
  const sendMessage = useCallback(
    async (text: string) => {
      const content = text.trim()
      if (!ticket || !content || sending) return

      if (!chatUnlocked) {
        if (!aiPrecheck(chatState, AI_FEATURE_LABELS.CHAT)) return
        if (!(await confirmConsume(AI_FEATURE_LABELS.CHAT, true))) return
      }

      const wasLocked = !chatUnlocked
      setMessages((prev) => [...prev, { role: 'user', content }])
      setDraft('')
      setSending(true)
      try {
        const response = await ticketApi.aiChat(ticket.id, content)
        setMessages((prev) => [...prev, { role: 'assistant', content: response.content }])
        // 解锁这一步改变了 quota.chat（AVAILABLE → USED），横幅、按钮文案、剩余次数
        // 徽标都得跟着更新。用发送**前**的口径判断，发完再判就永远是 false 了。
        if (wasLocked) await reloadQuota()
      } catch {
        // 业务错误（未解锁 / 额度不足 / 不是接单人）：撤掉那条永远不会有回答的气泡，
        // 并把问题还回输入框。
        setMessages((prev) => prev.slice(0, -1))
        setDraft(content)
      } finally {
        setSending(false)
      }
    },
    [ticket, sending, chatUnlocked, chatState, aiPrecheck, confirmConsume, reloadQuota]
  )

  return {
    quota,
    reloadQuota,

    analysisState,
    chatState,
    prdState,
    pairPaid,
    chatUnlocked,
    canAfford,
    prdDownloadable,

    analyzePhase,
    prdPhase,
    sending,
    messages,
    draft,
    setDraft,

    runAnalysis,
    downloadPrd,
    sendMessage
  }
}

export type TicketAi = ReturnType<typeof useTicketAi>
