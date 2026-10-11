<template>
  <div class="ticket-list">
    <div class="page-header">
      <h2>需求大厅</h2>
      <el-button type="primary" @click="goCreate" class="btn-glass btn-glass-primary">
        <el-icon><Plus /></el-icon> 发布需求
      </el-button>
    </div>

    <!--
      未登录时的活动横幅。访客能完整浏览大厅，这里是他唯一会被告知"注册有东西拿"
      的地方 —— 说清赠什么、能拿来干什么，转化才有依据。数字取自
      utils/ai-quota.ts，与后端赠送逻辑共用一份常量。
    -->
    <div v-if="!authStore.token" class="promo-banner glass-card">
      <div class="promo-left">
        <div class="promo-icon">
          <el-icon size="22"><Present /></el-icon>
        </div>
        <div>
          <div class="promo-title">{{ SIGNUP_PROMO_TITLE }}</div>
          <div class="promo-sub">AI 技术分析、PRD 文档生成、AI 开发对话，接单后即可使用</div>
        </div>
      </div>
      <div class="promo-actions">
        <el-button class="btn-glass" @click="authStore.openAuthCard('login')">登录</el-button>
        <el-button type="primary" class="btn-glass btn-glass-primary" @click="authStore.openAuthCard('register')">
          注册领额度
        </el-button>
      </div>
    </div>

    <!--
      统计卡片 —— 按身份分叉，也是个人流水**唯一**的入口。
      **访客不渲染**：这几张卡全是"我的"口径（我发布的/我接的/进行中），
      没有身份时它们只会显示一排 0，既无意义又挤掉了本该看到的大厅列表。
      访客固定落在「待接单」这一档。

      此处原来固定四张（待处理/开发中/待验收/已完成），其中三张是个人口径却摆在
      公共大厅顶上：发布者看到"开发中"时那是别人在开发的单，接单者看到"待验收"时
      同理。而侧边栏「待处理/我接的单/已完成/我的需求」说的又是同一批东西 ——
      同一件事两个入口、两份几乎一样的列表。现在卡片按身份发，侧边栏那几项删掉。
    -->
    <div class="stats-cards" v-if="authStore.token">
      <div
        v-for="card in cards"
        :key="card.key"
        class="stat-card glass-card"
        :class="{ active: activeScope === card.key }"
        @click="selectScope(card.key)"
      >
        <div class="stat-icon" :class="card.iconClass">
          <el-icon size="24"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
        <div class="stat-arrow">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <!-- 当前范围的一句话说明：卡名（"进行中"）不足以说清它装了哪三种状态 -->
    <p class="scope-desc">{{ authStore.token ? activeCard.desc : guestScopeDesc }}</p>

    <!-- 筛选栏。状态不在其中 —— 状态由卡片决定，两套状态选择器必然打架。
         这里剩下的三个是在当前范围内再缩小。 -->
    <el-card class="filter-card glass-card-static">
      <el-form :inline="true" :model="filters" class="filter-form">
        <!-- 只有一种状态的卡（待接单/已完成）不显示状态下拉：它只有一个选项，
             摆在那里是死 UI。多状态的卡才给，默认"全部"= 这张卡自己的状态集。 -->
        <el-form-item v-if="statusOptions.length > 1" label="状态" class="glass-form-item">
          <el-select
            v-model="filters.status"
            placeholder="全部"
            clearable
            style="width: 130px"
            class="glass-input"
            @change="reloadFromFirstPage"
          >
            <el-option v-for="s in statusOptions" :key="s" :label="statusLabel(s)" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" class="glass-form-item">
          <el-select v-model="filters.category" placeholder="全部" clearable style="width: 120px" class="glass-input">
            <el-option label="Bug" value="BUG" />
            <el-option label="咨询" value="CONSULT" />
            <el-option label="投诉" value="COMPLAINT" />
            <el-option label="建议" value="SUGGESTION" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" class="glass-form-item">
          <el-select v-model="filters.priority" placeholder="全部" clearable style="width: 100px" class="glass-input">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" class="glass-form-item">
          <el-input v-model="filters.keyword" placeholder="搜索标题/内容" clearable style="width: 180px" class="glass-input" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reloadFromFirstPage" class="btn-glass btn-glass-primary">搜索</el-button>
          <el-button @click="resetFilters" class="btn-glass">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 图例：只在当前列表里真的有预附单时才出现（一条都没有时白占一行） -->
    <div v-if="hasPrepaid" class="prepaid-legend">
      <el-icon><MagicStick /></el-icon>
      <span>带「AI 已解锁」标记的单：发布者已替他附上 1 次 AI 额度 —— 接单后 AI 分析与对话免费用，不扣你自己的次数。</span>
    </div>

    <!-- 工单卡片网格 -->
    <div class="tickets-grid" v-loading="loading">
      <div
        v-for="ticket in tickets"
        :key="ticket.id"
        class="ticket-card glass-card"
        :class="{ 'ticket-card-mine': ticket.creatorId === myId, 'ticket-card-ai': ticket.aiPrepaid }"
        @click="router.push(`/tickets/${ticket.id}`)"
      >
        <div class="ticket-card-header">
          <span class="ticket-id">#{{ ticket.id }}</span>
          <el-tag :type="getStatusType(ticket.status)" size="small" class="glass-tag">
            {{ statusLabel(ticket.status) }}
          </el-tag>
        </div>
        <div class="ticket-card-title">{{ ticket.title }}</div>
        <div class="ticket-card-content">{{ ticket.content }}</div>

        <!-- 驳回理由必须让接单者看见，否则他只知道"被拒了"却不知道改什么。
             这行原来只在「待处理」页有，那个页面已并入这里，功能不能丢。 -->
        <div v-if="ticket.status === 'REJECTED' && ticket.rejectionReason" class="reject-reason">
          驳回理由：{{ ticket.rejectionReason }}
        </div>

        <!-- 拖太久的提示。判据是接单时间而不是 deadline —— 实测库里 deadline
             基本全空（46 条里只有 1 条有值），按它判等于永远不触发。 -->
        <div v-if="staleHint(ticket)" class="stale-hint">
          <el-icon><Clock /></el-icon>
          <span>{{ staleHint(ticket) }}</span>
        </div>

        <!-- 项目信息 -->
        <div class="ticket-project-info">
          <el-tag v-if="ticket.projectType" size="small" type="info" class="glass-tag">
            {{ ticket.projectType }}
          </el-tag>
          <span v-if="ticket.budget" class="budget">¥{{ ticket.budget }}</span>
        </div>

        <!-- 技术栈 -->
        <div v-if="ticket.techStack" class="ticket-tech-stack">
          <el-tag v-for="tech in ticket.techStack.split(',').slice(0, 3)" :key="tech" size="small" type="info" class="glass-tag tech-tag">
            {{ tech.trim() }}
          </el-tag>
          <el-tag v-if="ticket.techStack.split(',').length > 3" size="small" type="info" class="glass-tag tech-tag">
            +{{ ticket.techStack.split(',').length - 3 }}
          </el-tag>
        </div>

        <!--
          发布者预附了 AI 额度：打标 + 给接单者的 CTA。
          刻意**不做**右上角飘带 —— 那个位置被 .ticket-card-mine::after 的「我的」占着，
          发布者看自己附过额度的单会在同一个角上叠两个飘带。
        -->
        <div v-if="ticket.aiPrepaid" class="ai-prepaid">
          <span class="ai-prepaid-badge">
            <el-icon><MagicStick /></el-icon>
            AI 已解锁
          </span>
          <span class="ai-prepaid-hint">发布者已附 1 次额度 · 接单后 AI 分析与对话免费</span>
        </div>

        <div class="ticket-card-footer">
          <div class="ticket-meta">
            <span v-if="ticket.priority" :class="`priority-${ticket.priority}`" class="priority-badge">
              {{ ticket.priority }}
            </span>
          </div>
          <div class="ticket-time">{{ formatDate(ticket.createdAt) }}</div>
        </div>

        <!--
          操作区。三个按钮的判据与工单详情页完全一致（同一件事在两处入口，
          判据必须同一套），只是这里多了一重"点卡片是跳转、点按钮是操作"的
          区分 —— 所有点击都 .stop，否则点「去验收」会先跳走。
        -->
        <div v-if="hasActions(ticket)" class="ticket-card-actions" @click.stop>
          <el-button
            v-if="canPickupOn(ticket)"
            size="small"
            type="success"
            class="btn-glass btn-glass-success"
            @click.stop="openPickup(ticket)"
          >
            接单
          </el-button>
          <el-button
            v-if="isMyWork(ticket)"
            size="small"
            type="primary"
            class="btn-glass btn-glass-primary"
            @click.stop="openComplete(ticket)"
          >
            去完成
          </el-button>
          <el-button
            v-if="needsMyReview(ticket)"
            size="small"
            type="success"
            class="btn-glass btn-glass-success"
            @click.stop="openReview(ticket)"
          >
            去验收
          </el-button>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && tickets.length === 0" class="empty-state">
        <el-icon size="48"><Document /></el-icon>
        <p>{{ emptyText }}</p>
      </div>
    </div>

    <!-- 加载更多 / 分页 -->
    <div class="load-more-container" v-if="tickets.length > 0">
      <el-button
        v-if="pagination.page * pagination.pageSize < pagination.total"
        @click="loadMore"
        class="btn-glass load-more-btn"
        :loading="loadingMore"
      >
        加载更多 ({{ pagination.total - tickets.length }} 剩余)
      </el-button>
      <div v-else class="no-more">没有更多了</div>
    </div>

    <!-- 接单 / 去完成 / 去验收：与工单详情页共用同一组弹窗组件 -->
    <PickupTicketDialog v-model="pickupVisible" :ticket="current" @done="reloadAll" />
    <CompleteDeliveryDialog v-model="completeVisible" :ticket="current" @done="reloadAll" />
    <ReviewDeliveryDialog v-model="reviewVisible" :ticket="current" @done="reloadAll" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ticketApi } from '@/api'
import { useTicketStore } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import {
  Plus, Clock, Loading, Finished, ArrowRight, Document, Suitcase, Folder, Present, MagicStick
} from '@element-plus/icons-vue'
import PickupTicketDialog from '@/components/PickupTicketDialog.vue'
import CompleteDeliveryDialog from '@/components/CompleteDeliveryDialog.vue'
import ReviewDeliveryDialog from '@/components/ReviewDeliveryDialog.vue'
import type { TicketDTO, TicketStatsDTO, TicketStatus, TicketCategory, Priority } from '@/types'
import { getStatusType, getStatusLabel } from '@/utils/ticket-labels'
import { SIGNUP_PROMO_TITLE } from '@/utils/ai-quota'

const router = useRouter()
const ticketStore = useTicketStore()
const authStore = useAuthStore()

const myId = computed(() => authStore.userInfo?.id)

/**
 * 当前列表里有没有"发布者预附额度"的单 —— 图例按它显示。
 *
 * 用「已加载的这一页」而不是全站计数：图例是用来解释眼前这些卡片上的标记的，
 * 眼前一条都没有却挂着一行图例，反而让人去找不存在的标记。
 */
const hasPrepaid = computed(() => tickets.value.some(t => t.aiPrepaid))

// ---------------- 范围（卡片）定义 ----------------

type ScopeKey = 'open' | 'created' | 'handled' | 'active' | 'done'

/** 归属谓词。决定这个范围是"谁的单"，以及要不要带 includeAll。 */
type Ownership = 'none' | 'creator' | 'handler' | 'participant'

interface CardDef {
  key: ScopeKey
  label: string
  desc: string
  icon: any
  iconClass: string
  value: number
  /** 这个范围允许的状态。既用来生成状态下拉的选项，也在用户没挑具体状态时
   *  作为 statuses 传给后端（status 与 statuses 互斥）。 */
  statuses: TicketStatus[]
  /** 点进这张卡时状态下拉的初始值（'' = 全部，即这张卡的整个状态集） */
  defaultStatus: TicketStatus | ''
  ownership: Ownership
}

/**
 * 「进行中」的状态集 —— 接单之后、验收通过之前的那一段。
 *
 * 与后端 TicketService.ACTIVE_STATUSES 是同一套口径，改一处必须改另一处。
 * 发成逗号串（不是数组）：数组形式能不能用取决于客户端怎么序列化，后端验证过
 * 的只有逗号串，见 types/index.ts 里 statuses 的注释。
 */
const ACTIVE_STATUSES: TicketStatus[] = ['ACCEPTED', 'PENDING_APPROVAL', 'REJECTED']

/** 状态下拉的展示顺序（不是 TicketStatus 的声明顺序） */
const ALL_STATUSES: TicketStatus[] = ['OPEN', 'ACCEPTED', 'PENDING_APPROVAL', 'REJECTED', 'COMPLETED', 'CLOSED']

const STALE_DAYS = 7

const stats = ref<TicketStatsDTO>({
  open: 0,
  accepted: 0,
  pendingApproval: 0,
  completed: 0,
  pendingApprovalAsCreator: 0,
  completedAsParticipant: 0,
  createdTotal: 0,
  handledTotal: 0,
  activeAsCreator: 0,
  activeAsHandler: 0
})

/**
 * 大厅的卡片按身份发。
 *
 * 发布者：他关心的是"我的单走到哪了"，别人的单在开发中和无关（那正是原来
 *         "开发中"那张卡的毛病）。所以给 待接单 / 我发布的 / 进行中 / 已完成。
 * 接单者：同一批状态，但归属全在 handlerId 那侧；第一张叫「可接单」而不是
 *         「待接单」—— 同一批单子，站在接单者这边说的是"这些我能接"。
 * 其余（USER/ADMIN）：USER 是"发布与接单都能做"的存量身份（库里 6 个老账号），
 *         也是主测试账号 maizijian86 的身份，只给一套会让它已有的单凭空消失，
 *         所以给 5 张，两边都保住。
 *
 * 「进行中」把三个状态合成一个数字，是因为它对发布者只表达一件事：我的单被
 * 别人接走了、还没验收通过。点开后按行内的状态标签区分是"开发中"还是"待我验收"。
 */
const cards = computed<CardDef[]>(() => {
  const s = stats.value
  const role = authStore.userInfo?.role
  const publisher = role === 'PUBLISHER'
  const handler = role === 'HANDLER'

  const openCard: CardDef = {
    key: 'open',
    // 同一批单子，只有接单者那一侧叫「可接单」—— 站在他那边说的是"这些我能接"；
    // 其余身份沿用「待接单」（发布者是"等人来接"，USER 是历史叫法，见下）。
    label: handler ? '可接单' : '待接单',
    desc: publisher
      ? '全站还没人接的需求。你发布的需求要等接单者主动来接 —— 这一格是别人发的单，你可以点进去看，但发布者身份不提供接单入口。'
      : '全站还没人接的需求，点进去可以直接接单。',
    icon: Clock,
    iconClass: 'stat-icon-pending',
    value: s.open,
    statuses: ['OPEN'],
    defaultStatus: 'OPEN',
    // 不带 includeAll：OPEN 本来就该走"大厅可见"那条谓词，接走的单不该出现在这里
    ownership: 'none'
  }

  const createdCard: CardDef = {
    key: 'created',
    label: '我发布的',
    desc: '我发布过的全部需求，不限状态 —— 包括已关闭这类在别的格里看不到的单。',
    icon: Folder,
    iconClass: 'stat-icon-resolved',
    value: s.createdTotal,
    statuses: ALL_STATUSES,
    defaultStatus: '',
    ownership: 'creator'
  }

  const handledCard: CardDef = {
    key: 'handled',
    label: '我接的单',
    desc: '我接过的全部单子，不限状态 —— 在做的和已完成的都在这里。',
    icon: Suitcase,
    iconClass: 'stat-icon-closed',
    value: s.handledTotal,
    statuses: ALL_STATUSES,
    defaultStatus: '',
    ownership: 'handler'
  }

  const doneCard: CardDef = {
    key: 'done',
    label: '已完成',
    desc: '我参与过、且已验收通过的工单 —— 我发布的和我接的都算。',
    icon: Finished,
    iconClass: 'stat-icon-closed',
    value: s.completedAsParticipant,
    statuses: ['COMPLETED'],
    defaultStatus: 'COMPLETED',
    // 双方都要看得见自己那单：发布者没接过单，按 handlerId 查恒为 0
    ownership: 'participant'
  }

  if (publisher) {
    return [
      openCard,
      createdCard,
      {
        key: 'active',
        label: '进行中',
        desc: '我发布的、已经有人在做但还没验收通过的单 —— 含开发中、待我验收、被驳回返工三种。',
        icon: Loading,
        iconClass: 'stat-icon-processing',
        value: s.activeAsCreator,
        statuses: ACTIVE_STATUSES,
        defaultStatus: '',
        ownership: 'creator'
      },
      doneCard
    ]
  }

  if (handler) {
    return [
      openCard,
      handledCard,
      {
        key: 'active',
        label: '进行中',
        desc: '我手上还没结的单 —— 含待我交付、已交付等对方验收、被驳回要返工三种。',
        icon: Loading,
        iconClass: 'stat-icon-processing',
        value: s.activeAsHandler,
        statuses: ACTIVE_STATUSES,
        defaultStatus: '',
        ownership: 'handler'
      },
      doneCard
    ]
  }

  // USER / ADMIN：两边都给。
  // 「进行中」放在「我发布的」后面、而不是紧挨「我接的单」：它是「我发布的」的子集
  // （creator 口径），挨着放才看得出这层关系；且这样三种身份布局里「进行中」都在
  // 同一个位置，位置本身就是"这是同一类东西"的提示。
  return [
    openCard,
    createdCard,
    {
      key: 'active',
      label: '进行中',
      desc: '我发布的、已经有人在做但还没验收通过的单。我接的单那边的在途进度去「我接的单」里看。',
      icon: Loading,
      iconClass: 'stat-icon-processing',
      value: s.activeAsCreator,
      statuses: ACTIVE_STATUSES,
      defaultStatus: '',
      ownership: 'creator'
    },
    handledCard,
    doneCard
  ]
})

const activeScope = ref<ScopeKey>('open')
const activeCard = computed(
  () => cards.value.find((c) => c.key === activeScope.value) || cards.value[0]
)

/** 状态下拉的选项 = 当前范围允许的状态 */
const statusOptions = computed<TicketStatus[]>(() => activeCard.value.statuses)

// ---------------- 列表 ----------------

const tickets = ref<TicketDTO[]>([])
const loading = ref(false)
const loadingMore = ref(false)

const filters = reactive({
  status: 'OPEN' as TicketStatus | '',
  category: '' as TicketCategory | '',
  priority: '' as Priority | '',
  keyword: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const emptyText = computed(() => (filters.category || filters.priority || filters.keyword
  ? '这个范围里没有匹配的工单'
  : `${activeCard.value.label}：暂时没有工单`))

/**
 * 访客的范围说明。
 *
 * 不能直接复用 activeCard.desc —— 那是按**已登录身份**写的，访客（role 为
 * undefined）会落到 USER 那一支，于是看到"点进去可以直接接单"这种他根本
 * 做不到的话。宁可多一句文案，也不要在第一步就给访客错误预期。
 */
const guestScopeDesc = '全站还没人接的需求，点进去可以看到完整详情。登录并接单后，就能使用 AI 辅助开发功能。'

/** 归属谓词 → 查询参数。只有带归属的范围才传 includeAll。 */
function ownershipParams(ownership: Ownership): {
  creatorId?: number
  handlerId?: number
  participantId?: number
  includeAll?: boolean
} {
  switch (ownership) {
    case 'creator':
      return { creatorId: myId.value, includeAll: true }
    case 'handler':
      return { handlerId: myId.value, includeAll: true }
    case 'participant':
      return { participantId: myId.value, includeAll: true }
    default:
      return {}
  }
}

async function loadTickets() {
  loading.value = true
  try {
    const card = activeCard.value
    const picked = filters.status
    const res = await ticketApi.listTickets({
      page: pagination.page,
      pageSize: pagination.pageSize,
      ...ownershipParams(card.ownership),
      // status 与 statuses 互斥，后端 statuses 优先（TicketService.buildSpecification）。
      // 挑了具体状态就传 status；没挑就用这张卡自己的状态集。
      status: picked || undefined,
      statuses: !picked && card.statuses.length ? card.statuses.join(',') : undefined,
      category: filters.category || undefined,
      priority: filters.priority || undefined,
      keyword: filters.keyword || undefined
    })
    if (pagination.page === 1) {
      tickets.value = res.data
    } else {
      tickets.value = [...tickets.value, ...res.data]
    }
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

/**
 * 拉个人统计。
 *
 * **访客直接跳过**：/stats 需要身份，后端会给 401；而 401 会被 axios 拦截器
 * 当成"登录已过期"，把访客的登录卡片弹出来 —— 他只是打开大厅看一眼，不该被
 * 一个他没做过的动作袭击。这里早退比在 catch 里容错干净。
 */
async function loadStats() {
  if (!authStore.token) return
  try {
    stats.value = await ticketApi.getStats()
  } catch {
    // ignore
  }
}

/**
 * 切范围。
 *
 * 不再有"再点一次取消选中"：屏幕上总得有一个范围（列表总得显示点什么），
 * 取消掉之后落在哪个范围上说不清。默认永远是第一张卡。
 *
 * 分类/优先级/关键词故意不清 —— 用户刚打了关键词、想换个范围再看看，是最常见的
 * 动作。但要注意：卡片上的数字是不带筛选的，一旦有筛选在生效，列表行数就会小于
 * 卡上的数字，这是搜索的固有行为，不是口径错位。
 */
function selectScope(key: ScopeKey) {
  if (activeScope.value === key) return
  activeScope.value = key
  filters.status = activeCard.value.defaultStatus
  pagination.page = 1
  tickets.value = []
  loadTickets()
}

function reloadFromFirstPage() {
  pagination.page = 1
  loadTickets()
}

/** 弹窗里做完一次操作后：列表和统计都得重拉，卡上的数字要跟着动 */
function reloadAll() {
  loadTickets()
  loadStats()
}

function loadMore() {
  loadingMore.value = true
  pagination.page++
  loadTickets().finally(() => {
    loadingMore.value = false
  })
}

function resetFilters() {
  filters.status = activeCard.value.defaultStatus
  filters.category = ''
  filters.priority = ''
  filters.keyword = ''
  pagination.page = 1
  loadTickets()
}

// ---------------- 卡片上的操作 ----------------

const pickupVisible = ref(false)
const completeVisible = ref(false)
const reviewVisible = ref(false)
const current = ref<TicketDTO | null>(null)

/**
 * 接单：没人接 + OPEN + 不是我的单 + 我不是发布者身份。四者缺一不可。
 *
 * 与 TicketDetail.vue 的 canPickup 是同一套判据 —— 同一个入口出现在两个地方，
 * 判据必须同一份，否则会出现"大厅有按钮、点进去没有"这种自相矛盾。
 *
 * 注意最后一条对**访客**恒为真（`undefined !== 'PUBLISHER'`），这是刻意保留的：
 * 访客看得到「接单」按钮，点下去由 openPickup 开登录卡片。原来这里是个 bug ——
 * 点了直接发请求、拿 401 —— 而不是"不该显示"。修的方式是让点击有正确的结果，
 * 不是把入口藏起来：一个访客看到"这里可以接单"，才是他注册的最强动机。
 */
function canPickupOn(t: TicketDTO) {
  return (
    !t.handlerId &&
    t.status === 'OPEN' &&
    t.creatorId !== myId.value &&
    authStore.userInfo?.role !== 'PUBLISHER'
  )
}

/** 去完成：我是这单的接单人，且它在我手上（开发中 / 被驳回要返工） */
function isMyWork(t: TicketDTO) {
  return t.handlerId === myId.value && (t.status === 'ACCEPTED' || t.status === 'REJECTED')
}

/** 去验收：我是这单的发布者，且它等我验收 */
function needsMyReview(t: TicketDTO) {
  return t.creatorId === myId.value && t.status === 'PENDING_APPROVAL'
}

function hasActions(t: TicketDTO) {
  return canPickupOn(t) || isMyWork(t) || needsMyReview(t)
}

function openPickup(t: TicketDTO) {
  // 访客点「接单」→ 开登录卡片，而不是发一个注定 401 的请求。
  // 接单是"我要站这边"的动作，背后必须有身份，这一步绕不过去。
  if (!authStore.token) {
    authStore.openAuthCard('login')
    return
  }
  current.value = t
  pickupVisible.value = true
}

function openComplete(t: TicketDTO) {
  current.value = t
  completeVisible.value = true
}

function openReview(t: TicketDTO) {
  current.value = t
  reviewVisible.value = true
}

/**
 * 发布需求。
 *
 * 访客点了直接开登录卡片，而不是先 push 到 /tickets/new 再被守卫弹回来：
 * 那是一段"页面闪一下、地址栏没变、什么也没发生"的体验，用户只会认为按钮坏了。
 */
function goCreate() {
  if (!authStore.token) {
    authStore.openAuthCard('login')
    return
  }
  router.push('/tickets/new')
}

// ---------------- 展示辅助 ----------------

function statusLabel(s: TicketStatus) {
  return getStatusLabel(s)
}

/**
 * 「拖太久了」的提示。
 *
 * 判据是接单时间（acceptedAt），不是 deadline：实测库里 8 条 ACCEPTED 和 8 条
 * PENDING_APPROVAL 的 deadline **全是 NULL**，按它判等于这段代码永远不触发。
 * acceptedAt 则是齐的（接单时必写），且能回答那个真问题 —— 这单卡了多久。
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
  return new Date(date).toLocaleString('zh-CN')
}

// 别处（工单详情、创建、编辑）改了数据就把大厅刷新一遍
watch(() => ticketStore.refreshTrigger, () => {
  loadTickets()
  loadStats()
})

onMounted(() => {
  // 默认落在第一张卡上（发布者「待接单」/ 接单者「可接单」），与 filters.status
  // 的初值 'OPEN' 一致，所以这里直接拉就行，不用先 selectScope。
  loadTickets()
  loadStats()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

/* ---------------- 未登录活动横幅 ---------------- */
.promo-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding: 18px 22px;
  margin-bottom: 20px;
  border: 1px solid rgba(255, 159, 10, 0.32);
  background: linear-gradient(135deg, rgba(255, 159, 10, 0.14), rgba(94, 92, 230, 0.14));
}

.promo-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.promo-icon {
  flex: none;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--accent-warning), var(--accent-secondary));
  box-shadow: 0 4px 16px rgba(255, 159, 10, 0.35);
}

.promo-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.promo-sub {
  font-size: 13px;
  color: var(--text-tertiary);
}

.promo-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

/* 统计卡片 */
/* auto-fill 而不是固定 4 列：身份不同卡片数是 4 或 5（存量 USER/ADMIN 拿 5 张），
   固定 4 列会让第 5 张孤零零换行、前 4 张被拉宽到变形。 */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 12px;
}

.stat-card {
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.1) 0%, transparent 50%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--glass-shadow-hover);
}

.stat-card:hover::before {
  opacity: 1;
}

.stat-card:active {
  transform: translateY(-1px);
}

.stat-card.active {
  border-color: var(--accent-primary) !important;
  box-shadow: 0 0 0 2px rgba(0, 122, 255, 0.3), var(--glass-shadow);
}

.stat-card.active .stat-icon {
  transform: scale(1.1);
}

.stat-arrow {
  color: var(--text-tertiary);
  transition: all 0.3s ease;
  opacity: 0;
  transform: translateX(-10px);
}

.stat-card:hover .stat-arrow,
.stat-card.active .stat-arrow {
  opacity: 1;
  transform: translateX(0);
  color: var(--accent-primary);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: transform 0.3s ease;
}

.stat-icon-pending {
  background: linear-gradient(135deg, var(--accent-warning), #ffb347);
  box-shadow: 0 4px 16px rgba(255, 159, 10, 0.4);
}

.stat-icon-processing {
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-info));
  box-shadow: 0 4px 16px rgba(0, 122, 255, 0.4);
}

.stat-icon-resolved {
  background: linear-gradient(135deg, var(--accent-success), #7dffb3);
  box-shadow: 0 4px 16px rgba(48, 209, 88, 0.4);
}

.stat-icon-closed {
  background: linear-gradient(135deg, var(--text-tertiary), var(--text-secondary));
  box-shadow: 0 4px 16px rgba(255, 255, 255, 0.1);
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

/* 当前范围说明 */
.scope-desc {
  margin: 0 0 20px 2px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
}

/* 工单卡片网格 */
.tickets-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
  min-height: 200px;
}

.ticket-card {
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
}

.ticket-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--glass-shadow-hover);
}

.ticket-card:active {
  transform: translateY(-2px);
  transition-duration: 0.1s;
}

/* 自己的工单卡片样式 */
.ticket-card-mine {
  border: 1px solid rgba(48, 209, 88, 0.5) !important;
  box-shadow: 0 0 0 1px rgba(48, 209, 88, 0.2), var(--glass-shadow);
}

.ticket-card-mine::after {
  content: '我的';
  position: absolute;
  top: 20px;
  right: 12px;
  background: linear-gradient(135deg, #30d155, #7dffb3);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

/* 发布者预附过 AI 额度的卡片：一层很淡的琥珀底 + 卡内的标记（见 .ai-prepaid）。
   刻意只用 background-image —— .ticket-card-mine 那边用了 !important 的 border 和
   自带的 box-shadow，动这两样中的一个就会把"我的"那圈绿边或阴影挤掉。 */
.ticket-card-ai {
  background-image: linear-gradient(180deg, rgba(230, 162, 60, 0.1), rgba(230, 162, 60, 0.02));
}

.ai-prepaid {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-prepaid-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #d9930a;
  background: rgba(230, 162, 60, 0.16);
  border: 1px solid rgba(230, 162, 60, 0.45);
}

.ai-prepaid-hint {
  font-size: 12px;
  color: var(--text-secondary);
}

/* 图例。自带底色，不挂 .glass-card-static —— 那个类的 background 会与本类的
   琥珀底打架，谁赢取决于两张样式表的加载顺序。 */
.prepaid-legend {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  color: var(--text-secondary);
  background: rgba(230, 162, 60, 0.12);
  border: 1px solid rgba(230, 162, 60, 0.35);
}

.prepaid-legend .el-icon {
  color: #d9930a;
  flex-shrink: 0;
}

.ticket-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ticket-id {
  color: var(--text-tertiary);
  font-size: 13px;
  font-weight: 500;
}

.ticket-card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ticket-card-content {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

/* 驳回理由：原来只在「待处理」页有，页面并入这里后跟着搬过来 */
.reject-reason {
  font-size: 12px;
  line-height: 1.5;
  color: var(--accent-danger, #f56c6c);
  padding: 6px 8px;
  border-radius: 6px;
  background: rgba(255, 69, 58, 0.1);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 拖太久的提示 */
.stale-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--accent-warning);
}

.ticket-project-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.budget {
  font-size: 14px;
  font-weight: 600;
  color: var(--accent-success);
}

.ticket-tech-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tech-tag {
  font-size: 11px !important;
  padding: 2px 6px !important;
}

.ticket-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
}

.ticket-meta {
  display: flex;
  gap: 8px;
  align-items: center;
}

.priority-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.priority-badge.priority-P0 { background: rgba(255, 69, 58, 0.2); color: var(--accent-danger); }
.priority-badge.priority-P1 { background: rgba(255, 159, 10, 0.2); color: var(--accent-warning); }
.priority-badge.priority-P2 { background: rgba(100, 210, 255, 0.2); color: var(--accent-info); }
.priority-badge.priority-P3 { background: rgba(255, 255, 255, 0.1); color: var(--text-tertiary); }

.ticket-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* 卡片上的操作按钮。上边框把它和卡片正文分开，免得看着像又一个标签行 */
.ticket-card-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid var(--glass-border);
}

/* 空状态 */
.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-tertiary);
  gap: 16px;
}

.empty-state p {
  font-size: 16px;
}

/* 加载更多 */
.load-more-container {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.load-more-btn {
  min-width: 200px;
}

.no-more {
  color: var(--text-tertiary);
  font-size: 14px;
}

.filter-card {
  margin-bottom: 20px;
}

.filter-form {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 16px;
}
</style>
