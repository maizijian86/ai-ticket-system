<template>
  <div class="ds-page hall">
    <!--
      首屏。DESIGN.md 的页面节奏是「白底编辑体大字开场 → 黑色窄带 → 白底内容 →
      色块叙事」。这一段承担的就是那个开场：一枚 mono 眉标 + 一行 display 大字 +
      一句 lead，除此之外什么都不放。

      登录后的主 CTA 落在这里（顶栏那两颗是导航级的），访客则由下面的浅紫促销条
      来承接 —— 同一个视口里始终只有一枚主按钮。
    -->
    <section class="hall-hero">
      <span class="ds-eyebrow hall-hero__eyebrow">REQUIREMENT HALL</span>
      <h1 class="ds-display-lg hall-hero__title">把需求交给对的人</h1>
      <p class="ds-body-lg hall-hero__lead">
        发布需求 · 接单开发 · AI 全程辅助。每张工单都能拿到技术分析、PRD 文档与任务拆解。
      </p>
      <div v-if="authStore.token" class="hall-hero__actions">
        <button class="ds-btn ds-btn--primary ds-btn--lg" @click="goCreate">
          <el-icon><Plus /></el-icon> 发布需求
        </button>
      </div>
    </section>

    <!--
      未登录时的活动横幅。访客能完整浏览大厅，这里是他唯一会被告知「注册有东西拿」
      的地方 —— 说清赠什么、能拿来干什么，转化才有依据。数字取自
      utils/ai-quota.ts，与后端赠送逻辑共用一份常量。

      用的是 DESIGN.md 的 promo-banner-lilac：浅紫整条 + 一枚品红 CTA。
      品红是**单发色**（文档：一页最多一枚促销 CTA），这一条就是它。
    -->
    <section v-if="!authStore.token" class="ds-promo hall-promo">
      <div class="hall-promo__left">
        <span class="hall-promo__icon">
          <el-icon size="18"><Present /></el-icon>
        </span>
        <div>
          <div class="hall-promo__title">{{ SIGNUP_PROMO_TITLE }}</div>
          <div class="hall-promo__sub">AI 技术分析、PRD 文档生成、AI 开发对话，接单后即可使用</div>
        </div>
      </div>
      <button class="ds-btn ds-btn--magenta" @click="authStore.openAuthCard('register')">
        注册领额度
      </button>
    </section>

    <!--
      范围卡片 —— 按身份分叉，也是个人流水**唯一**的入口。
      **访客不渲染**：这几张卡全是「我的」口径（我发布的/我接的/进行中），
      没有身份时它们只会显示一排 0，既无意义又挤掉了本该看到的大厅列表。
      访客固定落在「待接单」这一档。

      每张卡的图标落在一块小小的粉彩色砖上（lime/lilac/mint/coral/cream）——
      文档里 FigJam 那批「斜放的便利贴缩略图」用的就是这套语言：一排小色块读作
      拼贴，而**不是**四五块互相争抢视线的 color-block。选中的那张反过来压成
      纯黑面（文档：选中 = 主色面）。
    -->
    <section v-if="authStore.token" class="hall-scopes">
      <button
        v-for="card in cards"
        :key="card.key"
        class="stat-card"
        :class="{ 'is-active': activeScope === card.key }"
        @click="selectScope(card.key)"
      >
        <span class="stat-tile" :class="card.iconClass">
          <el-icon size="18"><component :is="card.icon" /></el-icon>
        </span>
        <span class="stat-body">
          <span class="stat-value">{{ card.value }}</span>
          <span class="stat-label">{{ card.label }}</span>
        </span>
        <el-icon class="stat-arrow" size="14"><ArrowRight /></el-icon>
      </button>
    </section>

    <!-- 当前范围的一句话说明：卡名（"进行中"）不足以说清它装了哪三种状态 -->
    <p class="scope-desc">{{ authStore.token ? activeCard.desc : guestScopeDesc }}</p>

    <!--
      筛选栏。状态不在其中 —— 状态由卡片决定，两套状态选择器必然打架。
      这里剩下的三个是在当前范围内再缩小。
    -->
    <section class="ds-card hall-filter">
      <el-form :inline="true" :model="filters" class="hall-filter__form">
        <!-- 只有一种状态的卡（待接单/已完成）不显示状态下拉：它只有一个选项，
             摆在那里是死 UI。多状态的卡才给，默认"全部"= 这张卡自己的状态集。 -->
        <el-form-item v-if="statusOptions.length > 1" label="状态">
          <el-select
            v-model="filters.status"
            placeholder="全部"
            clearable
            style="width: 130px"
            @change="reloadFromFirstPage"
          >
            <el-option v-for="s in statusOptions" :key="s" :label="statusLabel(s)" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.category" placeholder="全部" clearable style="width: 120px">
            <el-option label="Bug" value="BUG" />
            <el-option label="咨询" value="CONSULT" />
            <el-option label="投诉" value="COMPLAINT" />
            <el-option label="建议" value="SUGGESTION" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="filters.priority" placeholder="全部" clearable style="width: 100px">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="搜索标题/内容" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <button type="button" class="ds-btn ds-btn--primary" @click="reloadFromFirstPage">搜索</button>
          <button type="button" class="ds-btn ds-btn--secondary" @click="resetFilters">重置</button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 图例：只在当前列表里真的有预附单时才出现（一条都没有时白占一行） -->
    <div v-if="hasPrepaid" class="prepaid-legend">
      <el-icon size="15"><MagicStick /></el-icon>
      <span>带「AI 已解锁」标记的单：发布者已替他附上 1 次 AI 额度 —— 接单后 AI 分析与对话免费用，不扣你自己的次数。</span>
    </div>

    <!-- 工单卡片网格 -->
    <section class="tickets-grid" v-loading="loading">
      <article
        v-for="ticket in tickets"
        :key="ticket.id"
        class="ticket-card"
        :class="{ 'is-mine': ticket.creatorId === myId }"
        @click="router.push(`/tickets/${ticket.id}`)"
      >
        <header class="ticket-card__top">
          <span class="ds-chip ds-chip--mono">#{{ ticket.id }}</span>
          <span class="ticket-card__flags">
            <span v-if="ticket.creatorId === myId" class="ds-chip ds-chip--ink ds-chip--mono">MINE</span>
            <el-tag :type="getStatusType(ticket.status)" size="small">
              {{ statusLabel(ticket.status) }}
            </el-tag>
          </span>
        </header>

        <h3 class="ticket-card__title">{{ ticket.title }}</h3>
        <p class="ticket-card__content">{{ ticket.content }}</p>

        <!-- 驳回理由必须让接单者看见，否则他只知道"被拒了"却不知道改什么。
             这行原来只在「待处理」页有，那个页面已并入这里，功能不能丢。 -->
        <div v-if="ticket.status === 'REJECTED' && ticket.rejectionReason" class="ticket-note ticket-note--pink">
          驳回理由：{{ ticket.rejectionReason }}
        </div>

        <!-- 拖太久的提示。判据是接单时间而不是 deadline —— 实测库里 deadline
             基本全空（46 条里只有 1 条有值），按它判等于永远不触发。 -->
        <div v-if="staleHint(ticket)" class="ticket-note ticket-note--cream">
          <el-icon size="13"><Clock /></el-icon>
          <span>{{ staleHint(ticket) }}</span>
        </div>

        <!--
          发布者预附了 AI 额度：一条 lime 色带 —— 全站最想让接单者看见的信息
          就是这个，所以给它一整块颜色，而不是一个描边标签。
        -->
        <div v-if="ticket.aiPrepaid" class="ticket-prepaid">
          <span class="ticket-prepaid__badge">
            <el-icon size="13"><MagicStick /></el-icon> AI 已解锁
          </span>
          <span class="ticket-prepaid__hint">发布者已附 1 次额度 · 接单后 AI 分析与对话免费</span>
        </div>

        <!-- 项目信息 -->
        <div v-if="ticket.projectType || ticket.budget" class="ticket-card__meta">
          <span v-if="ticket.projectType" class="ds-chip">{{ ticket.projectType }}</span>
          <span v-if="ticket.budget" class="ticket-budget">¥{{ ticket.budget }}</span>
        </div>

        <!-- 技术栈 -->
        <div v-if="ticket.techStack" class="ticket-card__tech">
          <span
            v-for="tech in ticket.techStack.split(',').slice(0, 3)"
            :key="tech"
            class="ds-chip ds-chip--mono tech-chip"
          >
            {{ tech.trim() }}
          </span>
          <span v-if="ticket.techStack.split(',').length > 3" class="ds-chip ds-chip--mono tech-chip">
            +{{ ticket.techStack.split(',').length - 3 }}
          </span>
        </div>

        <footer class="ticket-card__footer">
          <span
            v-if="ticket.priority"
            class="priority-badge"
            :class="`priority-${ticket.priority}`"
          >
            {{ ticket.priority }}
          </span>
          <span class="ticket-card__time">{{ formatDate(ticket.createdAt) }}</span>
        </footer>

        <!--
          操作区。三个按钮的判据与工单详情页完全一致（同一件事在两处入口，
          判据必须同一套），只是这里多了一重"点卡片是跳转、点按钮是操作"的
          区分 —— 所有点击都 .stop，否则点「去验收」会先跳走。

          三个按钮互斥出现（接单 / 去完成 / 去验收），所以同为主色不会打架。
        -->
        <div v-if="hasActions(ticket)" class="ticket-card__actions" @click.stop>
          <button v-if="canPickupOn(ticket)" class="ds-btn ds-btn--primary ds-btn--sm" @click.stop="openPickup(ticket)">
            接单
          </button>
          <button v-if="isMyWork(ticket)" class="ds-btn ds-btn--primary ds-btn--sm" @click.stop="openComplete(ticket)">
            去完成
          </button>
          <button v-if="needsMyReview(ticket)" class="ds-btn ds-btn--primary ds-btn--sm" @click.stop="openReview(ticket)">
            去验收
          </button>
        </div>
      </article>

      <!-- 空状态 -->
      <div v-if="!loading && tickets.length === 0" class="empty-state">
        <span class="empty-state__icon">
          <el-icon size="26"><Document /></el-icon>
        </span>
        <span class="ds-caption empty-state__eyebrow">NO TICKETS</span>
        <p class="empty-state__text">{{ emptyText }}</p>
      </div>
    </section>

    <!-- 加载更多 / 分页 -->
    <div class="load-more-container" v-if="tickets.length > 0">
      <button
        v-if="pagination.page * pagination.pageSize < pagination.total"
        class="ds-btn ds-btn--secondary load-more-btn"
        :disabled="loadingMore"
        @click="loadMore"
      >
        {{ loadingMore ? '加载中…' : `加载更多（${pagination.total - tickets.length} 剩余）` }}
      </button>
      <span v-else class="ds-caption no-more">没有更多了</span>
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
  /**
   * 图标底下那块小色砖的类名。
   *
   * 取值来自 DESIGN.md 的 {colors.block-*} —— 这是全站唯一的色相来源，
   * 所以连"待接单"和"已完成"的区别也用它来表达，而不是另发明一套语义色。
   */
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
    iconClass: 'tile-lime',
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
    iconClass: 'tile-lilac',
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
    iconClass: 'tile-mint',
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
    iconClass: 'tile-cream',
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
        iconClass: 'tile-coral',
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
        iconClass: 'tile-coral',
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
      iconClass: 'tile-coral',
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
/* ============================================================
   首屏
   ============================================================ */
.hall-hero {
  padding-bottom: var(--ds-s-xxl);
  /* 这一整块是**浮在黑海上的**：.ds-page 没有自己的背景，海直接透上来。
     所以墨色(--ds-ink: #000)在这儿等于隐形，整块改用白色系，
     正文比标题低一档透明度，层次还是靠明度差撑。 */
  color: var(--ds-inverse-ink);
}

.hall-hero__eyebrow {
  display: block;
  margin-bottom: var(--ds-s-lg);
  /* 眉标自己有颜色（.ds-eyebrow），继承不到上面那行，得单独提亮 */
  color: rgba(255, 255, 255, 0.6);
}

/* 中文标题在 display-lg(64px) 下要收紧行高、并且限制行长 —— 一行超过
   约 12 个汉字就会散掉，max-width 让第二行只在必要时出现。 */
.hall-hero__title {
  max-width: 16ch;
  margin-bottom: var(--ds-s-md);
}

.hall-hero__lead {
  max-width: 52ch;
  margin-bottom: var(--ds-s-xl);
  color: rgba(255, 255, 255, 0.72);
}

.hall-hero__actions {
  display: flex;
  gap: var(--ds-s-sm);
  flex-wrap: wrap;
}

/* ============================================================
   访客促销条（浅紫 + 品红单发 CTA）
   ============================================================ */
.hall-promo {
  margin-bottom: var(--ds-s-xxl);
}

.hall-promo__left {
  display: flex;
  align-items: center;
  gap: var(--ds-s-md);
  min-width: 0;
}

.hall-promo__icon {
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: var(--ds-r-full);
  background: var(--ds-canvas);
  color: var(--ds-ink);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hall-promo__title {
  font-size: var(--ds-fs-body-lg);
  font-weight: var(--ds-fw-semi);
  letter-spacing: -0.2px;
  line-height: 1.3;
}

.hall-promo__sub {
  font-size: 15px;
  font-weight: var(--ds-fw-light);
  line-height: 1.45;
  margin-top: 2px;
}

/* ============================================================
   范围卡片（一排粉彩色砖 + 选中压黑）
   ============================================================ */
.hall-scopes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--ds-s-sm);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--ds-s-sm);
  text-align: left;
  background: var(--ds-canvas);
  border: 1px solid var(--ds-hairline);
  border-radius: var(--ds-r-lg);
  padding: 14px 16px;
  cursor: pointer;
  font-family: var(--ds-font-sans);
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease;
}

.stat-card:hover {
  border-color: var(--ds-ink);
}

.stat-tile {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: var(--ds-r-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ds-ink);
}

/* 五种色砖。取值全部来自 DESIGN.md 的 {colors.block-*}。 */
.tile-lime {
  background: var(--ds-block-lime);
}
.tile-lilac {
  background: var(--ds-block-lilac);
}
.tile-mint {
  background: var(--ds-block-mint);
}
.tile-coral {
  background: var(--ds-block-coral);
}
.tile-cream {
  background: var(--ds-block-cream);
}

.stat-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

/* 数字用 mono 并压到 24px/540 —— 它是这一格里唯一需要被"读出来"的东西 */
.stat-value {
  font-family: var(--ds-font-mono);
  font-size: 22px;
  font-weight: 500;
  line-height: 1.1;
  letter-spacing: -0.4px;
}

.stat-label {
  font-size: 13px;
  font-weight: var(--ds-fw-regular);
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stat-arrow {
  flex: none;
  opacity: 0.35;
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.stat-card:hover .stat-arrow {
  opacity: 1;
  transform: translateX(2px);
}

/* 选中 = 主色面。与顶栏、选项卡、分页当前页同一条规则：选中态画成一个
   正在生效的主 CTA，而不是一个被动的状态色。 */
.stat-card.is-active {
  background: var(--ds-primary);
  border-color: var(--ds-primary);
  color: var(--ds-on-primary);
}

.stat-card.is-active .stat-arrow {
  opacity: 0.7;
}

/* ============================================================
   范围说明 / 筛选栏
   ============================================================ */
.scope-desc {
  font-size: 15px;
  font-weight: var(--ds-fw-light);
  line-height: 1.55;
  color: var(--ds-ink);
  margin: var(--ds-s-md) 0 0;
  padding-left: var(--ds-s-md);
  border-left: 2px solid var(--ds-ink);
  max-width: 80ch;
}

.hall-filter {
  margin-top: var(--ds-s-lg);
  padding: var(--ds-s-md) var(--ds-s-lg) 0;
}

.hall-filter__form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 var(--ds-s-xs);
}

.hall-filter__form :deep(.el-form-item) {
  margin-bottom: var(--ds-s-md);
  margin-right: var(--ds-s-md);
}

/* 搜索/重置是一对原生胶囊，得跟 el-select 的高度对齐 */
.hall-filter__form :deep(.el-form-item:last-child .el-form-item__content) {
  display: flex;
  gap: var(--ds-s-xs);
}

/* ============================================================
   工单卡片
   ============================================================ */
.tickets-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--ds-s-lg);
  margin-top: var(--ds-s-lg);
  align-items: start;
}

.ticket-card {
  display: flex;
  flex-direction: column;
  gap: var(--ds-s-sm);
  background: var(--ds-canvas);
  border: 1px solid var(--ds-hairline);
  border-radius: var(--ds-r-lg);
  padding: var(--ds-s-lg);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.ticket-card:hover {
  border-color: var(--ds-ink);
  box-shadow: var(--ds-shadow-float);
  transform: translateY(-2px);
}

.ticket-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-s-xs);
}

.ticket-card__flags {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ticket-card__title {
  font-size: var(--ds-fs-body-lg);
  font-weight: var(--ds-fw-semi);
  line-height: 1.35;
  letter-spacing: -0.2px;
  color: var(--ds-ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ticket-card__content {
  font-size: 15px;
  font-weight: var(--ds-fw-light);
  line-height: 1.55;
  color: var(--ds-ink);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 卡片内的说明色带：驳回理由（粉）、拖太久（奶油）。都是小面积的粉彩，
   黑字压在上面比原来的彩底白字清楚得多。 */
.ticket-note {
  border-radius: var(--ds-r-sm);
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ds-ink);
}

.ticket-note--pink {
  background: var(--ds-block-pink);
}

.ticket-note--cream {
  background: var(--ds-block-cream);
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 预附额度：全站最想让接单者看见的一条，所以给整块 lime */
.ticket-prepaid {
  background: var(--ds-block-lime);
  border-radius: var(--ds-r-md);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.ticket-prepaid__badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: var(--ds-fw-semi);
  color: var(--ds-ink);
}

.ticket-prepaid__hint {
  font-size: 12px;
  line-height: 1.45;
  color: var(--ds-ink);
}

.ticket-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-s-xs);
}

.ticket-budget {
  font-family: var(--ds-font-mono);
  font-size: 18px;
  font-weight: 500;
  letter-spacing: -0.4px;
  color: var(--ds-ink);
}

.ticket-card__tech {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.tech-chip {
  font-size: 11px;
  padding: 2px 7px;
  background: var(--ds-surface-soft);
}

.ticket-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ds-s-xs);
  margin-top: auto;
  padding-top: var(--ds-s-sm);
  border-top: 1px solid var(--ds-hairline-soft);
}

/* 优先级徽标定义在 main.css（.priority-badge）：详情页的标题行也要用同一枚，
   放在这里的话那份 scoped 样式盖不到它。 */

.ticket-card__time {
  font-family: var(--ds-font-mono);
  font-size: 11px;
  letter-spacing: 0.4px;
  color: var(--ds-ink);
  opacity: 0.55; /* 时间戳是弱信息，这里用透明度而不是灰色 —— 单色系统里没有中灰这个角色 */
}

.ticket-card__actions {
  display: flex;
  gap: var(--ds-s-xs);
  margin-top: var(--ds-s-xxs);
}

/* ============================================================
   空状态 / 加载更多
   ============================================================ */
.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--ds-s-sm);
  padding: var(--ds-s-section) var(--ds-s-lg);
  background: var(--ds-surface-soft);
  border-radius: var(--ds-r-lg);
  text-align: center;
}

.empty-state__icon {
  width: 56px;
  height: 56px;
  border-radius: var(--ds-r-full);
  background: var(--ds-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ds-ink);
}

.empty-state__eyebrow {
  color: var(--ds-ink);
  opacity: 0.5;
}

.empty-state__text {
  font-size: var(--ds-fs-body-lg);
  font-weight: var(--ds-fw-normal);
  letter-spacing: -0.2px;
  color: var(--ds-ink);
}

.load-more-container {
  display: flex;
  justify-content: center;
  margin-top: var(--ds-s-xxl);
}

.load-more-btn {
  min-width: 240px;
}

.no-more {
  color: var(--ds-ink);
  opacity: 0.45;
  padding: var(--ds-s-xs) 0;
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1180px) {
  .tickets-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .tickets-grid {
    grid-template-columns: 1fr;
  }

  .hall-hero__title {
    max-width: none;
  }

  .hall-filter {
    padding: var(--ds-s-md) var(--ds-s-md) 0;
  }

  /* 移动端两个筛选按钮撑满一行，指尖好按 */
  .hall-filter__form :deep(.el-form-item:last-child) {
    width: 100%;
    margin-right: 0;
  }

  .hall-filter__form :deep(.el-form-item:last-child .el-form-item__content) {
    width: 100%;
  }

  .hall-filter__form :deep(.el-form-item:last-child .ds-btn) {
    flex: 1;
  }
}

@media (max-width: 560px) {
  .stat-card {
    padding: 12px 14px;
  }

  .hall-promo {
    padding: var(--ds-s-md);
  }
}
</style>
