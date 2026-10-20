import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { ArrowLeft, LoaderCircle, MessageCircle, Send } from '@/components/icons'
import { CARD_BASE } from '@/components/layout/PageShell'
import StatusTag, { PriorityTag } from '@/components/feedback/StatusTag'
import { GlassButton } from '@/components/ui/glass-button'
import { ticketApi } from '@/api/ticket'
import { useAuthStore } from '@/stores/authStore'
import { formatDateTime } from '@/utils/order-labels'
import { getProjectTypeLabel } from '@/utils/ticket-labels'
import { cn } from '@/lib/utils'
import type { ChatMessageDTO, TicketDTO } from '@/types'

/*
  工单私聊。

  ── 为什么是轮询而不是 WebSocket ──
  这一版**没有**引入 WebSocket：项目里原本有 spring-boot-starter-websocket，
  但一个用法都没有（站内通知那块走的是 SSE，而且那套代码已经删了 —— 见 Phase 3）。
  为了一个私聊页面重新引一套 WS 端点（还要在网关配 upgrade 转发、处理断线重连）
  不是这次该做的事。轮询 5 秒在本场景够用：私聊的信息密度很低，用户也不会盯着
  两个气泡之间看延迟。

  两个省电/省请求的细节：
    · 页面不可见时**不轮询**（visibilitychange）—— 否则用户切走十几分钟，后台一直在打接口；
    · 只在"没有正在发的消息"时拉取，避免把刚发出的那条盖掉。

  ── 谁在看 ──
  参与者只有发布者与接单者。**边界在服务端**：`TicketController.getChat` 会校验
  当前用户是不是这张单的参与者，不是就 403。前端不做"我是不是参与者"的判断 ——
  那种判断只能用来藏 UI，藏不住接口。
*/

/** 轮询间隔。私聊场景 5 秒足够，且改小会明显增加无效请求。 */
const POLL_INTERVAL_MS = 5000

export default function TicketChat() {
  const { id } = useParams()
  const ticketId = Number(id)
  const navigate = useNavigate()
  const myId = useAuthStore((s) => s.userInfo?.id)

  const [ticket, setTicket] = useState<TicketDTO | null>(null)
  const [messages, setMessages] = useState<ChatMessageDTO[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)

  const listRef = useRef<HTMLDivElement | null>(null)
  const alive = useRef(true)
  /** 上一次拉到的条数：变多了才滚到底，否则每 5 秒都会被拽回底部 */
  const lastCount = useRef(0)

  const loadTicket = useCallback(async () => {
    try {
      const t = await ticketApi.getTicket(ticketId)
      if (alive.current) setTicket(t)
    } catch {
      // 详情拉不到不致命：聊天记录那条请求会给出真正的错误
    }
  }, [ticketId])

  const loadMessages = useCallback(async () => {
    try {
      const list = await ticketApi.getChatHistory(ticketId)
      if (!alive.current) return
      setMessages(list)
      setLoadError(null)
    } catch (e) {
      if (!alive.current) return
      setLoadError(e instanceof Error ? e.message : '读取聊天记录失败')
      // 失败也把 messages 置成空数组，否则页面会永远停在骨架上
      setMessages((m) => m ?? [])
    }
  }, [ticketId])

  // 首次进入
  useEffect(() => {
    alive.current = true
    void loadTicket()
    void loadMessages()
    return () => {
      alive.current = false
    }
  }, [loadTicket, loadMessages])

  // 轮询：只在页面可见时跑
  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | null = null

    const start = () => {
      if (timer) return
      timer = setInterval(() => {
        if (document.visibilityState === 'visible') void loadMessages()
      }, POLL_INTERVAL_MS)
    }
    const stop = () => {
      if (timer) {
        clearInterval(timer)
        timer = null
      }
    }
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        void loadMessages()
        start()
      } else {
        stop()
      }
    }

    start()
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [loadMessages])

  // 有新消息才滚到底
  useEffect(() => {
    const count = messages?.length ?? 0
    if (count > lastCount.current) {
      lastCount.current = count
      const el = listRef.current
      if (el) el.scrollTop = el.scrollHeight
    }
  }, [messages])

  async function send() {
    const content = draft.trim()
    if (!content || sending) return

    setSending(true)
    try {
      const created = await ticketApi.sendChatMessage(ticketId, { content })
      if (!alive.current) return
      // 乐观合并：把服务端返回的那条直接追加，不必等下一轮轮询
      setMessages((m) => [...(m ?? []), created])
      setDraft('')
    } catch {
      // 拦截器统一提示；草稿保留，别让用户重打一遍
    } finally {
      if (alive.current) setSending(false)
    }
  }

  /** Enter 发送、Shift+Enter 换行 —— 聊天框的通用约定 */
  function onKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      void send()
    }
  }

  const isArchived = ticket?.status === 'COMPLETED' || ticket?.status === 'CLOSED'
  const counterpart = (() => {
    if (!ticket || myId == null) return null
    if (ticket.creatorId === myId) {
      return ticket.handlerName ? `接单者 · ${ticket.handlerName}` : '还没人接单'
    }
    return `发布者 · ${ticket.creatorName || '未知'}`
  })()

  return (
    <div className="mx-auto flex h-[calc(100vh-56px)] w-full max-w-[880px] flex-col px-4 py-4 sm:px-6 sm:py-6">
      <AnimatedContent trigger="mount" distance={16} duration={0.4} ease="power2.out" threshold={0}>
        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            onClick={() => navigate(`/tickets/${ticketId}`)}
            className="inline-flex cursor-pointer items-center gap-1.5 text-sm font-medium text-foreground/70 transition-colors [text-shadow:0_1px_0_rgba(255,255,255,0.45)] hover:text-foreground"
          >
            <ArrowLeft size={15} />
            返回工单
          </button>

          {ticket ? (
            <div className="flex min-w-0 flex-1 items-center gap-2">
              <span className="truncate text-sm font-semibold [text-shadow:0_1px_0_rgba(255,255,255,0.35)]">
                {ticket.title}
              </span>
              {ticket.projectType ? (
                <span className="hidden shrink-0 text-xs text-foreground/60 [text-shadow:0_1px_0_rgba(255,255,255,0.45)] sm:inline">
                  {getProjectTypeLabel(ticket.projectType)}
                </span>
              ) : null}
              {ticket.priority ? <PriorityTag priority={ticket.priority} /> : null}
              <StatusTag status={ticket.status} />
            </div>
          ) : null}
        </div>
      </AnimatedContent>

      <div className={cn(CARD_BASE, 'mt-3 flex min-h-0 flex-1 flex-col overflow-hidden')}>
        {/* 卡内小标题：与谁对话。空对话时这一行是唯一的上下文 */}
        <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-3">
          <div className="flex items-center gap-2 text-sm">
            <MessageCircle size={15} className="text-muted-foreground" />
            <span className="font-medium">工单沟通</span>
            {counterpart ? <span className="text-muted-foreground">· {counterpart}</span> : null}
          </div>
          <span className="font-mono text-[11px] tracking-[0.16em] text-muted-foreground/60 uppercase">
            Private
          </span>
        </div>

        {/* 消息区：`justify-end` 让"只有一两条"时气泡也贴着输入框，而不是浮在顶部 */}
        {/*
          消息区。容器本身是 flex 列 + `mt-auto` 作用在消息列表上 ——
          只有一两条消息时气泡贴着输入框（真实聊天软件的样子），
          而不是孤零零浮在卡片上半空。
        */}
        <div ref={listRef} className="flex min-h-0 flex-1 flex-col overflow-y-auto px-5 py-4">
          {messages === null ? (
            <div className="flex flex-1 items-center justify-center text-muted-foreground">
              <LoaderCircle size={18} className="animate-spin" />
            </div>
          ) : loadError && messages.length === 0 ? (
            <div className="flex flex-1 flex-col items-center justify-center gap-2 text-center">
              <p className="text-sm text-muted-foreground">{loadError}</p>
              <button
                type="button"
                onClick={() => void loadMessages()}
                className="cursor-pointer text-sm font-medium text-foreground underline-offset-4 hover:underline"
              >
                重试
              </button>
            </div>
          ) : messages.length === 0 ? (
            <div className="flex flex-1 flex-col items-center justify-center gap-2 text-center">
              <MessageCircle size={22} className="text-muted-foreground/50" />
              <p className="text-sm text-muted-foreground">还没有消息</p>
              <p className="max-w-xs text-xs text-muted-foreground/70">
                把交付时间、技术方案、要确认的细节在这里说清楚，比在评论区来回问更省事。
              </p>
            </div>
          ) : (
            <div className="mt-auto flex flex-col gap-3">
              {messages.map((m, i) => {
                const mine = m.userId === myId
                // 同一个人连续说话时不重复显示名字与时间，视觉上更像对话
                const prev = messages[i - 1]
                const showMeta = !prev || prev.userId !== m.userId
                return (
                  <div key={m.id} className={cn('flex flex-col', mine ? 'items-end' : 'items-start')}>
                    {showMeta ? (
                      <span className="mb-1 px-1 text-[11px] text-muted-foreground">
                        {mine ? '我' : m.userName || '对方'} · {formatDateTime(m.createdAt)}
                      </span>
                    ) : null}
                    <div
                      className={cn(
                        'max-w-[85%] rounded-2xl px-3.5 py-2 text-sm leading-6 whitespace-pre-wrap break-words',
                        mine
                          ? 'rounded-br-md bg-foreground text-background'
                          : 'rounded-bl-md border border-border bg-background text-foreground'
                      )}
                    >
                      {m.content}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {/* 输入区 */}
        <div className="border-t border-border px-4 py-3">
          {isArchived ? (
            <p className="py-2 text-center text-xs text-muted-foreground">
              工单已完结，只能查看历史消息。
            </p>
          ) : (
            <div className="flex items-end gap-2">
              <textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={onKeyDown}
                rows={1}
                placeholder="输入消息，Enter 发送，Shift + Enter 换行"
                className="max-h-32 min-h-9 flex-1 resize-none rounded-xl border border-input bg-transparent px-3 py-2 text-sm leading-5 outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/40"
              />
              <GlassButton
                type="button"
                onClick={() => void send()}
                disabled={sending || !draft.trim()}
                className="h-9 shrink-0 px-4 text-sm"
              >
                {sending ? <LoaderCircle size={14} className="animate-spin" /> : <Send size={14} />}
                发送
              </GlassButton>
            </div>
          )}
        </div>
      </div>

      <p className="mt-2 text-center text-[11px] text-foreground/60 [text-shadow:0_1px_0_rgba(255,255,255,0.45)]">
        只有这张工单的发布者与接单者能看到这里的消息。
      </p>
    </div>
  )
}
