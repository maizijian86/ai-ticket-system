import { useEffect, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import { ticketApi } from '@/api/ticket'
import {
  Briefcase,
  CircleCheckBig,
  Clock,
  Folder,
  LoaderCircle,
  Lock,
  Plus,
  Ticket as TicketIcon,
  WandSparkles
} from '@/components/icons'
import AnimatedContent from '@/components/animation/AnimatedContent'
import StatusTag, { PriorityTag } from '@/components/feedback/StatusTag'
import { CompleteDeliveryDialog } from '@/components/ticket/CompleteDeliveryDialog'
import { PickupConfirmMenu } from '@/components/ticket/PickupConfirmMenu'
import { ReviewDeliveryDialog } from '@/components/ticket/ReviewDeliveryDialog'
import { Button } from '@/components/ui/button'
import { GlassLayer } from '@/components/ui/glass-button'
import { Skeleton } from '@/components/ui/skeleton'
import { UserAvatar } from '@/components/user/UserAvatar'
import { useUserAvatars } from '@/hooks/useUserAvatars'
import { useAuthStore } from '@/stores/authStore'
import { useTicketStore } from '@/stores/ticketStore'
import { useUiStore } from '@/stores/uiStore'
import { getProjectTypeLabel } from '@/utils/ticket-labels'
import type {
  Priority,
  Role,
  TicketDTO,
  TicketQueryRequest,
  TicketStatsDTO,
  TicketStatus
} from '@/types'

const PAGE_SIZE = 12

/**
 * 哨兵进入预取区后，等这么久才真的拉下一页（用户 2026-10-19："加一个小等待时间"）。
 * 500ms 是个手感值：长到能把"甩滚轮时一扫而过"和"真的停在底部"分开，又不至于
 * 让人盯着底部发呆。改它只需要动这一个数 —— 观察器只负责起停计时器。
 */
const AUTO_LOAD_DELAY_MS = 500

/** 卡片上的图标尺寸，四处一致。 */
const TILE = 18

/**
 * 「进行中」的状态集 —— 接单之后、验收通过之前的那一段。
 *
 * 与后端 TicketService.ACTIVE_STATUSES 是同一套口径，改一处必须改另一处。
 * 发成逗号串（不是数组）：数组形式能不能用取决于客户端怎么序列化，后端验证过
 * 的只有逗号串，见 types/index.ts 里 statuses 的注释。
 */
const ACTIVE_STATUSES: TicketStatus[] = ['ACCEPTED', 'PENDING_APPROVAL', 'REJECTED']

const STALE_DAYS = 7

// ---------------- 四档筛选 ----------------

/**
 * 归属谓词。**这是整个大厅的隐私开关**，它决定一条查询"查的是谁的单"。
 *
 *   none        —— 谁的单都查（只有「待接单」用它，那本来就该是全站可见的公开池）
 *   creator     —— 只查我发布的
 *   handler     —— 只查我接的
 *   participant —— 只查我参与的（我发布的 **或** 我接的）
 *
 * 三个非 none 的档位一律配 includeAll=true：接单会把 is_visible 置 false，不带这个
 * 参数，自己接的单会从自己的列表里凭空消失。但注意 includeAll **只管可见性、不管
 * 归属** —— 归属永远由 creatorId / handlerId / participantId 显式给出，见 scopeParams()。
 *
 * ⚠️ creator 与 handler 是**两个不同的归属**，不能拿一个冒充另一个：`creatorId=我`
 * 在接单者的视角里恒为空（他基本不发单），`handlerId=我` 在发布者视角里同理。用户的
 * 「我的发布」「待完成」两格点开为空、或者装的是别人的单，历史上就是这里混了。
 */
type Ownership = 'none' | 'creator' | 'handler' | 'participant'

type ScopeKey = 'open' | 'created' | 'active' | 'handled' | 'done'

interface ScopeDef {
  key: ScopeKey
  label: string
  desc: string
  icon: typeof Clock
  ownership: Ownership
  /** 单状态。与 statuses 互斥（后端 buildSpecification 里 statuses 优先）。 */
  status?: TicketStatus
  /** 多状态。发成逗号串。 */
  statuses?: TicketStatus[]
  /** 卡片上那个数字取自 /stats 的哪个字段 —— 口径必须与这条查询完全一致。 */
  statKey: keyof TicketStatsDTO
}

/**
 * 大厅顶上的筛选卡片，**按当前登录者的角色分叉**（用户要求：要结合当前登录者的 ID
 * 和他的角色）。四张（发布者/接单者）或五张（USER/ADMIN 与访客）。
 *
 * 为什么分叉：发布者关心的是"我的单走到哪了"，接单者关心的是"我手上这单交了没有"，
 * 同两格数字（activeAsCreator / activeAsHandler）是**完全不同的人**在看的。用一套
 * 四格把两种口径混在一起，接单者点开「我的发布」永远是空的、发布者点开「我接的单」
 * 也是空的 —— 那不是"没数据"，那是拿错了人。
 *
 * 每一档的 status / ownership / statKey 三者**必须对齐**：卡片上的数字来自 /stats，
 * 列表来自 /tickets，两边同一口径才不至于出现"卡上写 3、点开是空的"。逐档核对过后端
 * TicketStatsDTO 的字段注释与 TicketService.getStats 的实现：
 *
 *   待接单/可接单  status=OPEN，无归属                 ← stats.open
 *                  （全站公开池，唯一一档会看到别人单的）
 *   我的发布       creatorId=我，**不限状态**           ← stats.createdTotal
 *                  （不传 status：后端没有状态谓词就是"全都要"，含已关闭）
 *   待完成         creatorId/handlerId=我 + ACTIVE      ← stats.activeAsCreator
 *                  （我这边还没结的：开发中/等我验收/被驳回返工三种。同一个 key
 *                    按角色长成两个口径，数字也从对应的 stats 字段取）
 *   我接的单       handlerId=我，**不限状态**           ← stats.handledTotal
 *   已完成         participantId=我 + COMPLETED         ← stats.completedAsParticipant
 *                  （我参与的都算 —— 发布者不该因为没接过单就查不到自己发布的已完成单）
 *
 * 原版 React 壳里那张**单列的「待审批」卡已经并进「待完成」**：PENDING_APPROVAL 本来
 * 就在 ACTIVE_STATUSES 里，单列一张等于把同一批单放在两个格子里，而"待我验收"这件事
 * 现在由行内的**绿色「去验收」按钮**直接指出来（比多一张卡更容易看见）。这也是旧版
 * Vue 的做法。
 */
function scopesFor(role: Role | undefined): ScopeDef[] {
  const openCard: ScopeDef = {
    key: 'open',
    // 同一批单子，只有接单者那一侧叫「可接单」—— 站在他那边说的是"这些我能接"。
    label: role === 'HANDLER' ? '可接单' : '待接单',
    desc:
      role === 'PUBLISHER'
        ? '全站还没人接的需求。这一格是别人发的单：点开可以看完整详情，但发布者身份不提供接单入口。'
        : '全站还没人接的需求，点进去可以直接接单；登录并接单后就能用 AI 辅助开发。',
    icon: Clock,
    ownership: 'none',
    status: 'OPEN',
    statKey: 'open'
  }

  const createdCard: ScopeDef = {
    key: 'created',
    label: '我的发布',
    desc: '我发布过的全部需求，不限状态 —— 还在等接单的、被人接走在做的、以及已关闭这种别的格里看不到的。',
    icon: Folder,
    ownership: 'creator',
    // status / statuses 都**不传**：后端 buildSpecification 在没有这两个条件时不加状态
    // 谓词，也就是"不限状态"。这才是"我的发布"该有的口径（它是个总览格）。
    statKey: 'createdTotal'
  }

  // 「待完成」= 同一个 key，按角色换归属与数字来源。两个字段**必须**跟着一起换：
  // 只换 ownership 不换 statKey，卡上就会显示另一个人的数字，而列表是他的。
  const activeCard = (ownership: 'creator' | 'handler', statKey: keyof TicketStatsDTO): ScopeDef => ({
    key: 'active',
    label: '待完成',
    desc: '我这边还没结的单 —— 含开发中（等你交付）、已交付（等对方验收）、被驳回要返工三种，按行内状态标签区分。',
    icon: LoaderCircle,
    ownership,
    statuses: ACTIVE_STATUSES,
    statKey
  })

  const handledCard: ScopeDef = {
    key: 'handled',
    label: '我接的单',
    desc: '我接过的全部单子，不限状态 —— 手上的和已完成的都在这里。',
    icon: Briefcase,
    ownership: 'handler',
    statKey: 'handledTotal'
  }

  const doneCard: ScopeDef = {
    key: 'done',
    label: '已完成',
    desc: '我参与过、且已验收通过的单 —— 我发布的和我接的都算。',
    icon: CircleCheckBig,
    ownership: 'participant',
    status: 'COMPLETED',
    statKey: 'completedAsParticipant'
  }

  // 发布者：他的单在哪、走到哪了
  if (role === 'PUBLISHER') return [openCard, createdCard, activeCard('creator', 'activeAsCreator'), doneCard]

  // 接单者：同一批状态，归属全在 handlerId 那侧
  if (role === 'HANDLER') return [openCard, handledCard, activeCard('handler', 'activeAsHandler'), doneCard]

  // USER / ADMIN 与**访客**：两边都给。
  // USER 是"发布与接单都能做"的存量身份（也是主测试账号的身份），只给一套会让它
  // 已有的单凭空消失；访客看到的也是这一套（除「待接单」外全部上锁），否则登录的
  // 一瞬间卡片的张数和位置会整排跳一下。
  // 「待完成」放在「我发布的」后面而不是紧挨「我接的单」：它是「我发布的」的子集
  // （creator 口径），挨着放才看得出这层关系。
  return [openCard, createdCard, activeCard('creator', 'activeAsCreator'), handledCard, doneCard]
}

/**
 * 按 key 取档位。**取不到就退回第一档（待接单）**，不抛错：角色的变化（登录/退出）
 * 会让当前的 key 在新的一套里不存在（比如 USER 选了「我接的单」之后又成了发布者），
 * 这时候静默回到「待接单」比炸掉整个大厅合理。
 */
function pickScope(scopes: ScopeDef[], key: ScopeKey): ScopeDef {
  return scopes.find((s) => s.key === key) ?? scopes[0]
}

/**
 * 归属谓词 → 查询参数。
 *
 * ⚠️ **`me` 为空的归属查询是绝对不能发出去的**：axios 会把值为 undefined 的键整个
 * 丢掉，于是 `creatorId: undefined, includeAll: true, status: 'PENDING_APPROVAL'`
 * 会变成一个**不带任何归属条件**的请求 —— 拿到的是全站每个人的待审批单，而且因为
 * 带了 includeAll，连 is_visible=false 的（也就是别人已经接走的）也一并返回。
 * 那不是"多查了一点"，那是把别人的单摊在访客面前。
 *
 * 所以这里对 me 为空的情况返回空对象（不发归属条件 = 也不发 includeAll），
 * 同时调用方 loadTickets() 里还有一道**在发请求之前**的早退。两道闸门都要有：
 * 这一层是"万一传错了不要放大成泄漏"，那一层是"根本不产生这个请求"。
 */
function scopeParams(scope: ScopeDef, me: number | undefined): TicketQueryRequest {
  switch (scope.ownership) {
    case 'creator':
      return me == null ? {} : { creatorId: me, includeAll: true }
    case 'handler':
      return me == null ? {} : { handlerId: me, includeAll: true }
    case 'participant':
      return me == null ? {} : { participantId: me, includeAll: true }
    default:
      return {}
  }
}

export default function TicketList() {
  const navigate = useNavigate()

  const token = useAuthStore((s) => s.token)
  const myId = useAuthStore((s) => s.userInfo?.id)
  const role = useAuthStore((s) => s.userInfo?.role)
  const openAuthCard = useUiStore((s) => s.openAuthCard)
  const refreshTrigger = useTicketStore((s) => s.refreshTrigger)
  const saveHall = useTicketStore((s) => s.saveHall)

  /**
   * 交付 / 验收两个弹窗的**目标工单**。null = 关着。
   *
   * 状态放在页面而不是卡里：弹窗是单实例（一次只可能点开一张单的），放进 TicketCard
   * 会让 12 张卡各挂一份 Dialog 的 DOM 与状态 —— 而每张卡的 Dialog 只有它自己用得上。
   */
  const [deliverTarget, setDeliverTarget] = useState<TicketDTO | null>(null)
  const [reviewTarget, setReviewTarget] = useState<TicketDTO | null>(null)

  /**
   * 从工单详情返回时，把大厅恢复成点进去之前的样子（档位 / 已加载的行 / 滚动位置）。
   *
   * 快照只在这里读一次：先比对身份，登录/退出/换号后的旧快照直接作废（里面可能是
   * 别人的单），随后**立刻清空** —— 它只服务"刚从一个详情页退回"这一次，之后从
   * 首页或收藏夹再进大厅就不该被一段旧滚动位置劫持。
   *
   * 恢复的是旧行，所以**首帧不显示骨架**（`loading` 从 false 起），并在后台静默
   * 重拉一次；重拉期间列表保持原高度，滚动位置不会因为骨架变矮而被浏览器夹掉。
   */
  const restoredRef = useRef(
    (() => {
      const snap = useTicketStore.getState().hall
      if (!snap) return null
      return snap.userId === myId && snap.role === role ? snap : null
    })()
  )
  const restored = restoredRef.current

  const [scopeKey, setScopeKey] = useState<ScopeKey>((restored?.scopeKey as ScopeKey) ?? 'open')
  const [tickets, setTickets] = useState<TicketDTO[]>(restored?.tickets ?? [])
  const [total, setTotal] = useState(restored?.total ?? 0)
  const [page, setPage] = useState(restored?.page ?? 1)
  const [loading, setLoading] = useState(restored == null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [stats, setStats] = useState<TicketStatsDTO | null>(restored?.stats ?? null)
  /**
   * 后端说还有下一页、但那一页返回了空数组时置位，用来掐掉自动加载 ——
   * 否则"滚到底 → 请求 → 仍是空 → 再滚到底"会变成一个死循环。
   * 换档位时清掉（见 selectScope）。
   */
  const [reachedEnd, setReachedEnd] = useState(false)

  /**
   * 正在被点开的那张卡（用户 2026-10-20："点击进入工单详情做一天从该工单展开的
   * 过渡动画"）。它只在**点击到导航发生**的这一小段时间里非空：给这张卡挂上
   * `view-transition-name: ticket-card`，浏览器才会把它当作共享元素，形变到详情页
   * 的标题卡上去。**同一时刻只能有一张卡带这个名字**，所以不按 id 拼名字、而是
   * 只给这一张加。
   */
  const [expandingId, setExpandingId] = useState<number | null>(null)

  /**
   * 发布者头像。工单接口只给 `creatorId`，头像走另一条批量接口 —— 放在页面层取，
   * 12 张卡共用一次请求，而不是让每张卡各挂一个 hook 发 12 次。
   */
  const avatars = useUserAvatars(tickets.map((t) => t.creatorId))

  const clearHall = useTicketStore((s) => s.clearHall)
  useEffect(() => {
    clearHall()
    if (restored?.scrollY) window.scrollTo(0, restored.scrollY)
    // 只跑一次：快照在本页生命周期里只消费一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /**
   * 请求序号。切范围/翻页很快时会有多个请求同时在飞，而它们**不保证按发出顺序
   * 返回**（越早发的越可能是慢的那个）。没有这个序号，先发后到的结果会盖掉后发
   * 先到的，表现为"点了『已完成』却显示『待接单』的行"。序号对不上就整个丢弃。
   */
  const seqRef = useRef(0)

  /**
   * 自动加载的防重入锁。
   *
   * `loadMore` 与「`loadingMore` 变成 true」之间隔着一次渲染，而
   * IntersectionObserver 在每次 observe 时都会补发一次初始状态。两发回调都执行
   * `setPage(p => p + 1)` 的话会一口气跳过一整页（第 2 页被翻到第 3 页，第 2 页
   * 永远不出现）。锁必须在调用点同步置位，不能依赖下一帧的 state。
   */
  const autoLoadingRef = useRef(false)

  /** 列表底部的哨兵。它进入视口（含 300px 预取余量）就自动拉下一页。 */
  const sentinelRef = useRef<HTMLDivElement | null>(null)

  /**
   * 上一次拉取时的「身份口径」（谁在看、以什么角色看）。
   *
   * 登录 / 退出会让 `myId`、`role` 在同一帧变化，而 fetch effect 的依赖里正好有
   * 这两个 —— 只追加不重置的话，用户停在第 2 页时一登录，第 2 页那 12 条会被
   * **再取一遍追加进去**（卡片重复 + React 重复 key 警告）。身份一换，旧行就不再
   * 属于新口径，必须退回第 1 页整段重拉。
   */
  const identityRef = useRef<string | null>(null)

  const scopes = scopesFor(role)
  // 高亮跟着**实际用的那一档**（pickScope 的返回值）走，而不是跟着 scopeKey：
  // 角色一变，旧的 key 可能在新的一套里不存在，这时列表显示的是「待接单」，
  // 却没有任何一张卡是高亮的 —— 比没有高亮更让人迷惑。
  const scope = pickScope(scopes, scopeKey)
  /** 需要登录才能查的档位（「待接单」以外全部） */
  const scoped = scope.ownership !== 'none'
  /** 访客落在需要登录的档位上 = 这一档被锁住了 */
  const locked = scoped && myId == null
  /** 上锁的档位张数，说明文案里要用真实的数（四张还是五张由角色决定） */
  const lockedCount = scopes.filter((s) => s.ownership !== 'none').length

  useEffect(() => {
    // 闸门之一：没有身份就绝不发归属查询（理由见 scopeParams 的注释）
    if (scoped && myId == null) {
      seqRef.current++ // 作废在飞的那一次，否则它的结果会填回一个已被锁住的列表
      setTickets([])
      setTotal(0)
      setPage(1)
      // 退回公开那一档。登录态没了还停在"我的"视图上，只会剩一个空列表加一句
      // 说不清的说明；而且继续停在上面，用户下次切回来会以为自己没有单。
      setScopeKey('open')
      setLoading(false)
      return
    }

    const seq = ++seqRef.current

    /*
      身份口径变了（登录 / 退出 / 换角色）：旧行作废，从第 1 页重来。
      见 identityRef 的注释 —— 不这么做就会把当前页再追加一遍。
    */
    const identity = `${myId}:${role}`
    const identityChanged = identityRef.current !== null && identityRef.current !== identity
    identityRef.current = identity
    if (identityChanged) {
      setTickets([])
      setTotal(0)
      setReachedEnd(false)
      autoLoadingRef.current = false
      setPage(1)
    }
    const effectivePage = identityChanged ? 1 : page

    /*
      恢复快照后的第一次拉取：不能走"第 2 页 append"那条路 —— 旧行已经在 state 里，
      append 会把同一批行再加一遍。这里一次要回 `已加载页数 × 每页条数` 条（后端
      pageSize 没有上限），用第一页的入参把整段换回来，页数保持不变，"加载更多"
      接着往下翻。
    */
    const restoring = identityChanged ? null : restoredRef.current
    restoredRef.current = null
    const append = effectivePage > 1 && !restoring
    if (append) setLoadingMore(true)
    else if (!restoring) setLoading(true)

    const params: TicketQueryRequest = {
      page: restoring ? 1 : effectivePage,
      pageSize: restoring ? page * PAGE_SIZE : PAGE_SIZE,
      ...scopeParams(scope, myId),
      // status 与 statuses 互斥，后端 statuses 优先（TicketService.buildSpecification）。
      status: scope.status,
      statuses: scope.statuses?.join(',')
    }

    ticketApi
      .listTickets(params)
      .then((res) => {
        if (seq !== seqRef.current) return
        setTickets((prev) => (append ? [...prev, ...res.data] : res.data))
        setTotal(res.total)
        // 总数说还有、这一页却没人：停下自动加载，别让观察器把我们拖进死循环
        if (append && res.data.length === 0) setReachedEnd(true)
      })
      .catch(() => {
        // 拦截器已经 toast 过错误了，这里只负责别让加载态挂住。失败时清空列表，
        // 免得上一档的行留在屏幕上冒充这一档的结果。
        if (seq !== seqRef.current) return
        // 恢复后的静默刷新失败时保留旧行：那是用户刚刚看过的列表，清空比过期更糟
        if (!append && !restoring) {
          setTickets([])
          setTotal(0)
        }
      })
      .finally(() => {
        if (seq !== seqRef.current) return
        setLoading(false)
        setLoadingMore(false)
        autoLoadingRef.current = false
      })
    // scope 由 scopeKey + role 共同决定（同一批 key 在不同角色下会长成不同的归属，
    // 见 scopesFor），所以 role 必须在依赖里：登录/退出时归属口径整个换了一套。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scopeKey, page, myId, role, refreshTrigger])

  /**
   * 拉个人统计（卡片上的数字）。
   *
   * **访客直接跳过**：/stats 需要身份，后端会给 401；而 401 会被 axios 拦截器
   * 当成"登录已过期"，把访客的登录卡片弹出来 —— 他只是打开大厅看一眼，不该被
   * 一个他没做过的动作袭击。这里早退比在 catch 里容错干净。
   */
  useEffect(() => {
    if (!token) {
      setStats(null)
      return
    }
    let alive = true
    ticketApi
      .getStats()
      .then((s) => {
        if (alive) setStats(s)
      })
      .catch(() => {
        /* 数字拿不到不该影响列表，保持 null（卡上显示 —） */
      })
    return () => {
      alive = false
    }
  }, [token, refreshTrigger])

  function selectScope(key: ScopeKey) {
    const next = pickScope(scopes, key)
    // 访客点锁住的那些档 → 弹登录卡片，而不是发一个注定 401 或（更糟）发一个
    // 不带归属条件、把别人的单查出来的请求。见 scopeParams 的注释。
    if (next.ownership !== 'none' && myId == null) {
      openAuthCard('login')
      return
    }
    // 比的是**实际在显示的那一档**（scope.key），不是 scopeKey —— 两者在角色切换后
    // 可能不一致（见 scope 的注释）。
    if (key === scope.key) return
    setScopeKey(key)
    setPage(1)
    setTickets([])
    setTotal(0)
    // 新档位的第 1 页还没来，旧档位的"到底了"与在飞的自动加载都不再成立
    setReachedEnd(false)
    autoLoadingRef.current = false
  }

  /**
   * 进详情前把大厅定格（档位 / 已加载的行 / 滚动位置），返回时由本页恢复。
   * 存 `scope.key` 而不是 `scopeKey`：角色切换后两者可能不一致，快照要存**实际
   * 显示的那一档**，否则回来会恢复到一个并不存在的档位。
   */
  function openTicket(t: TicketDTO) {
    saveHall({
      userId: myId,
      role,
      scopeKey: scope.key,
      tickets,
      total,
      page,
      stats,
      scrollY: window.scrollY,
      refreshTrigger
    })
    /*
      从这张卡展开到详情页（用户 2026-10-20）。三步缺一不可：

      1. `flushSync` 先把 `expandingId` 提交到 DOM。旧画面是在 navigate 那一刻截的，
         普通 setState 要等下一帧才渲染 —— 名字落不到旧画面上，共享元素不成立。
      2. `state: { ticket }` 把列表里这份数据带过去，详情页首帧就有标题卡可画
         （同时省掉一次骨架闪烁），共享元素才有"落点"。详情页随后照常静默重拉。
      3. `viewTransition: true` 交给 React Router 包 `document.startViewTransition`。
    */
    flushSync(() => setExpandingId(t.id))
    navigate(`/tickets/${t.id}`, { state: { ticket: t }, viewTransition: true })
  }

  /**
   * 自动翻页的唯一入口。
   *
   * 原来这里是「加载更多」按钮的点击处理器，现在由底部哨兵触发（用户 2026-10-19：
   * "划到工单底部后自动加载下一页"）。保留函数而不是内联进观察器，是因为闸门有
   * 三条：整页加载中、已经在翻、以及同步防重入锁。
   */
  function loadMore() {
    if (loading || autoLoadingRef.current) return
    autoLoadingRef.current = true
    setPage((p) => p + 1)
  }

  function goCreate() {
    // 访客点了直接开登录卡片，而不是先 push 到 /tickets/new 再被守卫弹回来：
    // 那是一段"页面闪一下、地址栏没变、什么也没发生"的体验，用户只会认为按钮坏了。
    if (!token) {
      openAuthCard('login')
      return
    }
    navigate('/tickets/new')
  }

  const hasMore = !reachedEnd && page * PAGE_SIZE < total

  /**
   * 滚到底部自动翻页。用户 2026-10-19 要求"加一个小等待时间"：
   * 哨兵进入预取区后**先停 500ms 再拉**，不是一露头就发请求。
   *
   * 这个停顿解决的是手感问题：快速往下甩滚轮时，哨兵会被一瞬间的滚动噪声扫过，
   * 立刻拉取等于"我还没打算看下一页，它已经加载了"；停一下再确认它仍然在预取区
   * 里，才认为用户真的到底了。停顿期间哨兵文案不变（"继续下滑加载更多"），
   * 真正发请求后才会变成转圈。
   *
   * 观察器在**每次依赖变化时重建**（loadingMore / tickets.length），这是有意的：
   * ① 追加完成后哨兵往往还停在预取区里，重建时浏览器会补发一次初始回调，
   *    "新的一页没把视口填满"那种情况才会继续往下拉；
   * ② 换档位会清空列表并整页重载，重建保证旧观察器不会对着已卸载的哨兵发呆。
   *
   * rootMargin 留 300px：卡片高 360，等于"最后一行刚露头就开始计时"，走完那
   * 500ms 时多半已经滚到最底，下一批正好接上，不会先看到一段空白再等。
   */
  useEffect(() => {
    if (loading || !hasMore) return
    const el = sentinelRef.current
    if (!el) return
    let timer: number | null = null
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          // 同一个哨兵只留一个计时器：观察器反复回调不该叠出多次 loadMore
          if (timer === null) {
            timer = window.setTimeout(() => {
              timer = null
              loadMore()
            }, AUTO_LOAD_DELAY_MS)
          }
          return
        }
        // 已经被滚出预取区（用户又划回去了）：取消这次等待
        if (timer !== null) {
          window.clearTimeout(timer)
          timer = null
        }
      },
      { rootMargin: '0px 0px 300px 0px' }
    )
    io.observe(el)
    return () => {
      if (timer !== null) window.clearTimeout(timer)
      io.disconnect()
    }
    // loadMore 是每次渲染新建的闭包，但它的行为只依赖 loading / ref / setPage，
    // 进依赖表只会让观察器每帧重建；这里用 state 当语义依赖。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, hasMore, loadingMore, tickets.length])

  return (
    <div className="mx-auto w-full max-w-[1200px] px-6 py-8">
      {/*
        大厅打开动画（用户 2026-10-19 给的 AnimatedContent 示例参数，逐字使用：
        distance 100 / duration 0.8 / power3.out / blur 0 / stagger 0.08 / threshold 0.1）。

        **只包住列表以上的三块**（标题行、四档筛选卡、档位说明）。工单卡不在这里：
        滚动追加的卡片要在"自己挂载"时入场，用各自的 AnimatedContent（见下面网格）；
        再把整个列表包一层就会双重位移 —— 卡片在容器里动、容器自己也在动。
      */}
      <AnimatedContent
        distance={100}
        direction="vertical"
        duration={0.8}
        ease="power3.out"
        blur={0}
        stagger={0.08}
        threshold={0.1}
      >
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="font-mono text-xs font-medium tracking-[0.24em] text-muted-foreground uppercase">
            Requirement Hall
          </p>
          <h1 className="mt-1.5 text-2xl font-semibold tracking-tight sm:text-3xl">需求大厅</h1>
        </div>
        {/*
          「发布需求」只给 PUBLISHER（用户 2026-10-19 要求：其他角色都没有入口）。
          颜色与卡片上的「接单」一致（emerald-600 白字），形状/尺寸保持原来的胶囊
          （h-9 px-5 text-sm rounded-full）—— 用户明确要求"形状大小不变"。
          这里用实心按钮而不是玻璃：接单按钮就是实心的，两块主操作要一眼同款。
        */}
        {role === 'PUBLISHER' && (
          <button
            type="button"
            onClick={goCreate}
            className="inline-flex h-9 cursor-pointer items-center justify-center gap-2 rounded-full bg-emerald-600 px-5 text-sm font-semibold text-white transition-colors hover:bg-emerald-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/50"
          >
            <Plus size={15} />
            发布需求
          </button>
        )}
      </div>

      {/*
        筛选卡片：**张数与标签都由角色决定**（见 scopesFor），访客也看得见这些卡 ——
        藏起来的话，他既不知道登录后能拿到什么，布局还会在登录的一瞬间跳一下。
        所以锁住的那些只是变暗 + 挂一把锁，点下去弹登录卡片。卡上没有数字（显示 —），
        因为 /stats 对访客根本不该调。

        列数是**写死的两套类名**（4 张 → lg:grid-cols-4，5 张 → md:grid-cols-3
        lg:grid-cols-5），不是拼出来的 —— Tailwind 扫不到动态类名，拼出来的那一份
        样式根本不会被生成。5 张在 lg 下挤成 5 列：卡里的数字是主角、标签只有一行，
        160px 出头够用；挤不下就会换行，宁可让它挤也不要在窄屏上排成 1 列长条。

        **这些块是玻璃的**（「发布需求」那套，见 GlassLayer），原来铺的是白卡。换成
        玻璃之后有两个连带决定：

        - **"变暗"只加在内层内容上，不能加在按钮上。** 祖先一旦 `opacity < 1` 就成了
          backdrop root，玻璃能折射的范围被关进按钮内部，干净的玻璃退成一块糊的 ——
          而锁住的那些档恰恰最需要让人看出"这是块能点的玻璃，点它会弹登录卡"。
        - **选中态是一块纯白盖在玻璃上**（用户 2026-10-19 指定的做法）。白层画在玻璃
          **上面**、内容下面，靠 DOM 顺序 + z 值保证：玻璃是无 z-index 的定位元素，
          白层 `z-[5]`、内容 `z-10`。**别改成给按钮换底色** —— 底色在玻璃底下，会被
          `backdrop-filter` 采进折射里，等于没画；也别给白层加 opacity，半透明的白会
          透出折射，看起来像一块脏玻璃。（`border` 一直占着 1px 的位、只换颜色，免得
          hover 时按钮宽高跳一下。）
      */}
      <section
        className={[
          'mt-6 grid grid-cols-2 gap-3',
          scopes.length === 5 ? 'md:grid-cols-3 lg:grid-cols-5' : 'lg:grid-cols-4'
        ].join(' ')}
      >
        {scopes.map((s) => {
          const active = s.key === scope.key
          const Icon = s.icon
          const isLocked = s.ownership !== 'none' && myId == null
          return (
            <button
              key={s.key}
              type="button"
              aria-pressed={active}
              onClick={() => selectScope(s.key)}
              className={[
                'relative cursor-pointer rounded-2xl border text-left transition-colors',
                active ? 'border-foreground/35' : 'border-foreground/15 hover:border-foreground/30'
              ].join(' ')}
            >
              <GlassLayer radius={16} />
              {/* 选中 = 纯白盖满玻璃；未选中时透明，整块卡就是玻璃本身 */}
              {active && (
                <span aria-hidden="true" className="absolute inset-0 z-[5] rounded-2xl bg-white" />
              )}
              {/* 内边距在内层：玻璃要铺满按钮的整个盒子（含内边距），不能只铺内容那一圈 */}
              <span
                className={[
                  'relative z-10 flex items-center gap-3 px-4 py-3.5',
                  isLocked && 'opacity-60'
                ]
                  .filter(Boolean)
                  .join(' ')}
              >
                <span
                  className={[
                    'flex size-10 shrink-0 items-center justify-center rounded-xl',
                    active ? 'bg-foreground/12 text-foreground' : 'bg-muted text-muted-foreground'
                  ].join(' ')}
                >
                  <Icon size={TILE} />
                </span>
                <span className="min-w-0">
                  <span className="block text-lg leading-tight font-semibold tabular-nums">
                    {stats ? stats[s.statKey] : '—'}
                  </span>
                  {/*
                    标签用 `text-foreground/70` 而不是 `text-muted-foreground`：卡面现在
                    不是白底了，底下是**饱和的蓝**（虹彩），那支中灰压上去读不清 ——
                    实测截图里"我的发布（待完成）"几个字几乎化在背景里。近黑墨色压蓝
                    对比度才够。
                  */}
                  <span className="mt-0.5 block text-xs leading-4 text-foreground/70">
                    {s.label}
                  </span>
                </span>
              </span>
              {isLocked && (
                <Lock size={13} className="absolute top-3 right-3 z-10 text-foreground/70" />
              )}
            </button>
          )
        })}
      </section>

      {/* 当前档位的一句话说明：卡名（"待完成"）不足以说清它装了哪一批单 */}
      <p className="mt-3 text-xs leading-5 text-muted-foreground">
        {locked ? `这${lockedCount}档查的都是跟你自己相关的单，登录后可见。` : scope.desc}
      </p>
      </AnimatedContent>

      {/* 工单卡片网格 */}
      <section className="mt-6">
        {loading ? (
          /*
            骨架条用与真卡片同一套栅格（同样的行高口径），只是不写死高度 —— 于是
            加载态和加载完的排版**一样**：读数据那一两秒里卡片不会整体塌下去再弹回来。
          */
          <div className="grid auto-rows-[minmax(360px,1fr)] gap-5 sm:grid-cols-2 xl:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="rounded-[1.5rem]" />
            ))}
          </div>
        ) : tickets.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-3xl border border-border bg-muted/40 px-6 py-20 text-center">
            <span className="flex size-12 items-center justify-center rounded-2xl bg-card text-muted-foreground shadow-sm">
              <TicketIcon size={22} />
            </span>
            <p className="font-mono text-xs tracking-[0.2em] text-muted-foreground uppercase">
              No tickets
            </p>
            <p className="text-sm text-muted-foreground">{scope.label}：暂时没有工单</p>
          </div>
        ) : (
          <>
            {/*
              **所有卡片一样大**（用户要求：每个工单的长宽比要一致）。

              做法是 `auto-rows-[minmax(360px,1fr)]` —— 每行至少 360px，且 `1fr` 让
              **所有行取同一个高度**（= 最高的那一行）。留着默认的 `align-items: stretch`
              （也就是**不要**再写 `items-start`，那个正是"高度随内容各走各的"的开关），
              卡片就被这一行抻平。小括号里那两个数各管一段：

              - `1fr` 管的是"行与行之间也一致"。只靠 stretch 的话，同一行的三张一样高，
                但下一行会按它自己的内容重新算 —— 看着还是一排排参差。
              - `360px` 只是个下限，给"这一档的单恰好都很短"（只有标题 + 一行正文）兜底，
                否则整屏会是几条扁条。它不是目标高度：内容多的一行会比它高。

              卡里那条 `mt-auto` 的页脚跟着这个一起做：卡片被抻高时多出来的空白落在
              正文和页脚之间，三张卡的页脚落在同一水平线上 —— 参差感主要就是从那儿来的。
            */}
            <div className="grid auto-rows-[minmax(360px,1fr)] gap-5 sm:grid-cols-2 xl:grid-cols-3">
              {tickets.map((t, i) => (
                /*
                  每张卡一个入场层（用户 2026-10-19："划到工单底部后自动加载下一页
                  一样用动画"）。`trigger="mount"` —— 追加进来的卡是挂载时入场的，
                  已有的卡不会因为列表变长而重播（AnimatedContent 的 effect 依赖里
                  没有 index，`(i % PAGE_SIZE)` 对同一张卡恒等于同一个延迟）。

                  参数与大厅打开动画一致（距离/时长/缓动/blur 都照用户示例），只有
                  stagger 换成了 delay：stagger 只作用于**直接子元素**，一张卡一个
                  包裹层时它没有意义。

                  `h-full` 是布局前提：包裹层成了网格项（被 stretch 抻到行高），
                  里面的卡片必须跟着填满，否则同一行的卡片会各按内容高度参差。
                */
                <AnimatedContent
                  key={t.id}
                  trigger="mount"
                  distance={100}
                  direction="vertical"
                  duration={0.8}
                  ease="power3.out"
                  blur={0}
                  delay={(i % PAGE_SIZE) * 0.08}
                  className="h-full"
                >
                  <TicketCard
                    ticket={t}
                    creatorAvatarUrl={avatars[t.creatorId]}
                    myId={myId}
                    role={role}
                    loggedIn={!!token}
                    expanding={t.id === expandingId}
                    onOpen={() => openTicket(t)}
                    onDeliver={() => setDeliverTarget(t)}
                    onReview={() => setReviewTarget(t)}
                  />
                </AnimatedContent>
              ))}
            </div>

            {/*
              自动翻页的哨兵（用户 2026-10-19："划到工单底部后自动加载下一页"）。
              原来的「加载更多（N 剩余）」按钮下架 —— 它要求用户再点一次，而滚到底
              这个动作本身已经表达了"我要更多"。哨兵只承担两件事：被观察到时拉下一页、
              拉取期间显示一个转圈。

              **不要把这个 div 换成纯占位高度**：观察器认的就是它的 rect；高度为 0
              也能观察，但"加载中"的文案就没地方放了。
            */}
            <div className="mt-8 flex justify-center">
              {hasMore ? (
                <div
                  ref={sentinelRef}
                  aria-live="polite"
                  className="inline-flex h-9 items-center gap-2 text-sm text-muted-foreground"
                >
                  {loadingMore ? (
                    <>
                      <LoaderCircle className="size-4 animate-spin" />
                      加载中…
                    </>
                  ) : (
                    '继续下滑加载更多'
                  )}
                </div>
              ) : (
                <span className="font-mono text-xs tracking-[0.2em] text-muted-foreground uppercase">
                  — 没有更多了 —
                </span>
              )}
            </div>
          </>
        )}
      </section>

      {/*
        交付 / 验收弹窗：单实例挂在页面级（理由见上面两个 target 状态的注释）。
        `open` 写死 true + 条件挂载：弹窗的存在与否就由 target 是否有值表达，两层
        状态（渲染与否 / 打开与否）只留一层，不会出现"关掉了但还挂在 DOM 里、
        下次点开时残留上一次的工单"。
      */}
      {deliverTarget && (
        <CompleteDeliveryDialog
          ticket={deliverTarget}
          open
          onOpenChange={(next) => {
            if (!next) setDeliverTarget(null)
          }}
        />
      )}
      {reviewTarget && (
        <ReviewDeliveryDialog
          ticket={reviewTarget}
          open
          onOpenChange={(next) => {
            if (!next) setReviewTarget(null)
          }}
        />
      )}
    </div>
  )
}

/**
 * 一张工单一张卡。
 *
 * 整张卡是入口（点击/回车进详情），所以带 role/tabIndex，键盘用户也能走到。
 *
 * **卡上不再有任何鼠标悬浮效果**（用户要求去掉）。原来是两层：一层是
 * `hover:-translate-y-0.5` 的抬起位移，另一层是 SpotlightCard 跟着指针跑的光斑
 * 和描边。这里把整个 SpotlightCard 换成了普通 div —— 只去掉位移的话，光斑还在，
 * 那才是更显眼的那个"悬浮效果"；而且 SpotlightCard 每张卡都要挂一套**全局**
 * pointermove/scroll 监听 + rAF + IntersectionObserver，12 张卡就是 12 套，
 * 只为画一个已经不要了的效果。
 *
 * 卡面还是**白卡压在虹彩上**那套值（白底 + 1px 墨边 + 一层投下去的影），照原来
 * light palette 抄的（边 rgba(24,24,27,.1~.16)、影 0 16px 36px -18px .3）——
 * 白面背后就是虹彩，边和影弱一点卡片的轮廓就化在背景里（表现为"两张卡粘在一起"）。
 * 这里没用玻璃：这 12 张卡压着大段正文，折射会让字在玻璃里浮动；玻璃留给筛选栏
 * 和按钮那种"动作"元素（见上面那四块和自己顶上的「发布需求」）。
 *
 * 布局上 `flex flex-col` + 页脚 `mt-auto` 是为"所有卡片一样大"服务的：卡片被栅格
 * 抻高时，多出来的空白全落在正文和页脚之间，页脚因此贴底。
 */
function TicketCard({
  ticket: t,
  creatorAvatarUrl,
  myId,
  role,
  loggedIn,
  expanding,
  onOpen,
  onDeliver,
  onReview
}: {
  ticket: TicketDTO
  /** 发布者头像地址，由页面批量取好传下来；拿不到就是 undefined，走首字母兜底 */
  creatorAvatarUrl?: string
  myId: number | undefined
  role: Role | undefined
  loggedIn: boolean
  /**
   * 这张卡正被点开：挂上 View Transition 的共享元素名。
   * 只有被点的那一张为 true —— 两张同时带同一个名字会让整段过渡失效。
   */
  expanding?: boolean
  onOpen: () => void
  onDeliver: () => void
  onReview: () => void
}) {
  const isMine = t.creatorId === myId
  const techs = t.techStack ? t.techStack.split(',').map((s) => s.trim()).filter(Boolean) : []
  const stale = staleHint(t)
  const showPickup = canPickupOn(t, myId, role)
  const showDeliver = isMyWork(t, myId)
  const showReview = needsMyReview(t, myId)
  const hasActions = showPickup || showDeliver || showReview

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label={`查看工单 #${t.id}：${t.title}`}
      onClick={onOpen}
      style={expanding ? { viewTransitionName: 'ticket-card' } : undefined}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onOpen()
        }
      }}
      className="flex h-full cursor-pointer flex-col rounded-[1.5rem] border border-border bg-card p-8 shadow-[0_1px_2px_rgba(24,24,27,0.07),0_16px_36px_-18px_rgba(24,24,27,0.3)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-foreground/50"
    >
      <header className="flex items-center gap-2">
        <span className="font-mono text-xs text-muted-foreground">#{t.id}</span>
        {isMine && (
          <span className="rounded border border-foreground/25 bg-foreground/8 px-1.5 py-0.5 font-mono text-[11px] text-foreground">
            MINE
          </span>
        )}
        <span className="ml-auto">
          <StatusTag status={t.status} />
        </span>
      </header>

      <h3 className="mt-3 text-lg leading-snug font-semibold tracking-tight">{t.title}</h3>
      <p className="mt-2 line-clamp-3 text-sm leading-6 text-muted-foreground">{t.content}</p>

      {/*
        驳回理由必须让接单者看见，否则他只知道"被拒了"却不知道改什么。
      */}
      {t.status === 'REJECTED' && t.rejectionReason && (
        <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs leading-5 text-red-700">
          驳回理由：{t.rejectionReason}
        </div>
      )}

      {/*
        「拖太久了」的提示。判据是接单时间而不是 deadline —— 实测库里 deadline 基本
        全空，按它判等于永远不触发。见 staleHint()。
      */}
      {stale && (
        <div className="mt-3 flex items-center gap-1.5 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-700">
          <Clock size={13} className="shrink-0" />
          <span>{stale}</span>
        </div>
      )}

      {/*
        发布者预附了 AI 额度：一整条 lime 色带，而不是一个描边标签。全站最想让
        接单者看见的信息就是这个，所以给它一整块颜色。
      */}
      {t.aiPrepaid && (
        <div className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border border-lime-300 bg-lime-50 px-3 py-2">
          <span className="inline-flex items-center gap-1 text-xs font-semibold text-lime-700">
            <WandSparkles size={13} />
            AI 已解锁
          </span>
          <span className="text-xs text-lime-700/80">
            发布者已附 1 次额度 · 接单后 AI 分析与对话免费
          </span>
        </div>
      )}

      {(t.projectType || t.budget) && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          {t.projectType && (
            <span className="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground">
              {getProjectTypeLabel(t.projectType)}
            </span>
          )}
          {t.budget ? (
            <span className="text-sm font-semibold text-foreground">¥{t.budget}</span>
          ) : null}
        </div>
      )}

      {techs.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {techs.slice(0, 3).map((tech) => (
            <span
              key={tech}
              className="rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground"
            >
              {tech}
            </span>
          ))}
          {techs.length > 3 && (
            <span className="rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">
              +{techs.length - 3}
            </span>
          )}
        </div>
      )}

      {/*
        动作区 + 页脚。`mt-auto` 从页脚移到了这一层：卡片被栅格抻高时多出来的空白
        要落在正文和"这一块"之间，页脚才会贴底 —— 而动作区必须**跟着页脚一起**待在
        底部（动作是这张卡上最该被看到的东西，不能浮在正文和页脚中间）。
      */}
      <div className="mt-auto flex flex-col gap-3">
        {hasActions && (
          /*
            ⚠️ **这一层的 onClick / onKeyDown stopPropagation 不是可选项。**
            整张卡是个 role="button"（点击/回车进详情），而 Popover / Dialog 的内容
            虽然被 portal 到了 body 下，事件在 **React 树**上仍然是从这里冒上去的
            —— 也就是说：点「确认接单」会顺带把详情页打开；在菜单里按回车同理。
            拦在这一层比在每个子组件里各拦一次可靠（它们并不知道自己被摆在了可点
            容器里，将来新加一个按钮也不会漏）。
          */
          <div
            className="flex items-center justify-end gap-2"
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.stopPropagation()}
          >
            {showPickup && <PickupConfirmMenu ticket={t} loggedIn={loggedIn} />}
            {showDeliver && (
              <Button size="sm" onClick={onDeliver}>
                去完成
              </Button>
            )}
            {showReview && (
              <Button size="sm" onClick={onReview}>
                去验收
              </Button>
            )}
          </div>
        )}

        <footer className="flex items-center gap-2 border-t border-border pt-4 text-xs text-muted-foreground">
          {t.priority && <PriorityTag priority={t.priority as Priority} />}
          <span className="flex min-w-0 items-center gap-1.5">
            <UserAvatar
              avatarUrl={creatorAvatarUrl}
              name={t.creatorName}
              className="size-5 shrink-0"
            />
            <span className="truncate">{t.creatorName || `用户 ${t.creatorId}`}</span>
          </span>
          {t.handlerName && <span className="truncate">· 接单 {t.handlerName}</span>}
          <span className="ml-auto shrink-0">{formatDate(t.createdAt)}</span>
        </footer>
      </div>
    </div>
  )
}

/**
 * 卡上那三个动作的判据。**与详情页的 canPickup 是同一套**（用户报过"大厅有按钮、
 * 点进去没有"这种自相矛盾）—— 改动时两边要一起改。
 *
 * 注意 `myId != null` 那两道：访客的 myId 是 undefined，而**没有接单人的工单
 * handlerId 也是 undefined**，`undefined === undefined` 会成立 —— 少写这个判断，
 * 访客看每一张待接单的卡都会多出一个「去完成」按钮（点了是 401）。
 */
function canPickupOn(t: TicketDTO, myId: number | undefined, role: Role | undefined) {
  // 最后一条对访客恒为真（`undefined !== 'PUBLISHER'`），这是**刻意保留的**：
  // 访客看得到「接单」按钮，点下去由 PickupConfirmMenu 弹出登录卡片。一个访客看到
  // "这里可以接单"，才是他注册的最强动机；把入口藏起来反而是错的。
  return !t.handlerId && t.status === 'OPEN' && t.creatorId !== myId && role !== 'PUBLISHER'
}

/** 去完成：我是这单的接单人，且它在我手上（开发中 / 被驳回要返工） */
function isMyWork(t: TicketDTO, myId: number | undefined) {
  return myId != null && t.handlerId === myId && (t.status === 'ACCEPTED' || t.status === 'REJECTED')
}

/** 去验收：我是这单的发布者，且它等我验收 */
function needsMyReview(t: TicketDTO, myId: number | undefined) {
  return myId != null && t.creatorId === myId && t.status === 'PENDING_APPROVAL'
}

/**
 * 「拖太久了」的提示。
 *
 * 两种状态等的东西相反，所以文案分开：
 *   ACCEPTED / REJECTED   → 等接单人干活，催他
 *   PENDING_APPROVAL      → 已经交货了，等的是发布者自己
 */
function staleHint(t: TicketDTO): string {
  if (!t.acceptedAt) return ''
  if (!ACTIVE_STATUSES.includes(t.status)) return ''
  const days = Math.floor((Date.now() - new Date(t.acceptedAt).getTime()) / 86400000)
  if (days < STALE_DAYS) return ''
  return t.status === 'PENDING_APPROVAL'
    ? `已交付 ${days} 天，等你验收`
    : `已接单 ${days} 天未交付`
}

function formatDate(date: string) {
  return new Date(date).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
