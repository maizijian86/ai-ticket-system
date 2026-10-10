<template>
  <div class="ticket-detail">
    <div class="page-header">
      <el-button text @click="router.push('/tickets')" class="btn-glass">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
    </div>

    <div v-loading="loading">
      <template v-if="ticket">
        <!-- 工单头部 -->
        <el-card class="ticket-header-card glass-card">
          <div class="ticket-header">
            <div class="ticket-title-row">
              <span class="ticket-id">#{{ ticket.id }}</span>
              <h1>{{ ticket.title }}</h1>
            </div>
            <div class="ticket-tags">
              <el-tag v-if="ticket.projectType" size="small" class="glass-tag">
                {{ ticket.projectType }}
              </el-tag>
              <el-tag v-if="ticket.category" :type="getCategoryType(ticket.category)" size="small" class="glass-tag">
                {{ getCategoryLabel(ticket.category) }}
              </el-tag>
              <el-tag v-if="ticket.priority" size="small" :type="getPriorityType(ticket.priority)" class="glass-tag">
                {{ ticket.priority }}
              </el-tag>
              <el-tag :type="getStatusType(ticket.status)" size="small" class="glass-tag">
                {{ getStatusLabel(ticket.status) }}
              </el-tag>
            </div>
          </div>

          <div class="ticket-meta">
            <div class="meta-item">
              <span class="label">发布人</span>
              <span>{{ ticket.creatorName }}</span>
            </div>
            <div class="meta-item">
              <span class="label">接单人</span>
              <span>{{ ticket.handlerName || '待接单' }}</span>
            </div>
            <div class="meta-item">
              <span class="label">预算</span>
              <span>{{ ticket.budget ? `¥${ticket.budget}` : '面议' }}</span>
            </div>
            <div class="meta-item">
              <span class="label">截止日期</span>
              <span>{{ ticket.deadline ? formatDate(ticket.deadline) : '待定' }}</span>
            </div>
          </div>

          <!-- 技术栈 -->
          <div v-if="ticket.techStack" class="tech-stack">
            <span class="label">技术栈</span>
            <div class="tech-tags">
              <el-tag v-for="tech in ticket.techStack.split(',')" :key="tech" size="small" type="info" class="glass-tag">
                {{ tech.trim() }}
              </el-tag>
            </div>
          </div>

          <!--
            AI 摘要。`aiContentVisible` 是"访客看不到任何 AI 生成内容"那条规则的落点：
            后端会把 aiSummary 连同工单一起返回（它属于工单字段，不单独鉴权），
            所以对访客的隐藏必须发生在这里。
          -->
          <div v-if="ticket.aiSummary && aiContentVisible" class="ai-summary glass-card-static">
            <div class="ai-summary-header">
              <el-icon><ChatDotRound /></el-icon>
              <span>AI 摘要</span>
            </div>
            <div class="ai-summary-content">{{ ticket.aiSummary }}</div>
          </div>

          <!-- 操作按钮 -->
          <div class="ticket-actions">
            <!-- 接单按钮 - 只有非发布者且工单未接单时显示 -->
            <!--
              判定用 handlerId 而不是 acceptedBy。后者是另一条已删除的接单路径写的字段，
              与 handlerId 各写一个，于是"已接单"的工单在这里仍然显示接单按钮。
              三个条件缺一不可：没人接 + 状态为 OPEN + 我不是发布者。
            -->
            <el-button
              v-if="canPickup"
              type="success"
              @click="openPickupDialog"
              class="btn-glass btn-glass-success"
            >
              <el-icon><Check /></el-icon> 接单
            </el-button>

            <!--
              进入聊天 - 只有这一单的发布者和接单人看得到；归档后私聊已被清空，
              入口一并撤掉。
              此前只判 ticket.handlerId（"这张单有人接了"）而不判"是不是我"，
              于是**任何登录用户**都能看到这个入口 —— 点进去才被后端 403 挡下。
            -->
            <el-button
              v-if="(isCreator || isHandler) && !isArchived"
              type="primary"
              @click="router.push(`/tickets/${ticket.id}/chat`)"
              class="btn-glass btn-glass-primary"
            >
              <el-icon><ChatDotRound /></el-icon> 进入聊天
            </el-button>

            <!-- 去完成 - 接单人推进自己的单 -->
            <el-button
              v-if="isHandler && (ticket.status === 'ACCEPTED' || ticket.status === 'REJECTED')"
              type="primary"
              @click="openCompleteDialog"
              class="btn-glass btn-glass-primary"
            >
              <el-icon><Finished /></el-icon> 去完成
            </el-button>

            <!-- 验收 - 发布者验收待验收的交付 -->
            <el-button
              v-if="isCreator && ticket.status === 'PENDING_APPROVAL'"
              type="success"
              @click="openReviewDialog"
              class="btn-glass btn-glass-success"
            >
              <el-icon><CircleCheck /></el-icon> 验收
            </el-button>

            <!-- 编辑 - 只有发布者本人、且工单未完结 -->
            <el-button
              v-if="canEdit"
              @click="router.push(`/tickets/${ticket.id}/edit`)"
              class="btn-glass"
            >
              编辑
            </el-button>

            <!--
              此处原有「AI开发分析」按钮（v-if="status === 'OPEN'"）已删除 ——
              AI 卡片里有「点击生成技术建议」「点击生成任务拆解」两个按钮，是重复入口。
              注意那两个按钮**不判状态**，所以 AI 分析现在在 ACCEPTED/PENDING_APPROVAL
              上也变得可点了：这是想要的，别给它们补 status 判断，否则「任务拆解」
              在开发中就永远生不出来。
            -->
          </div>
        </el-card>

        <!-- 交付凭证 - 接单者提交后显示 -->
        <el-card v-if="ticket.completionProof" class="ticket-content-card glass-card">
          <div class="ticket-content">
            <h3>交付凭证</h3>
            <div class="content-text">
              <!--
                只做纯文本插值，绝不 v-html：这是接单者填的自由文本、会被存库，
                用 v-html 渲染等于把存储型 XSS 直接送到发布者浏览器。
                渲染成链接时也只认 http(s)，挡掉 javascript: 之类的伪协议。
              -->
              <a
                v-if="/^https?:\/\/\S+/i.test(ticket.completionProof.trim())"
                :href="ticket.completionProof"
                target="_blank"
                rel="noopener noreferrer"
                class="proof-link"
              >{{ ticket.completionProof }}</a>
              <span v-else>{{ ticket.completionProof }}</span>
            </div>
          </div>
        </el-card>

        <!-- 驳回理由 - 让接单者知道要改什么 -->
        <el-card v-if="ticket.status === 'REJECTED' && ticket.rejectionReason" class="ticket-content-card glass-card">
          <div class="ticket-content">
            <h3>驳回理由</h3>
            <div class="content-text">{{ ticket.rejectionReason }}</div>
          </div>
        </el-card>

        <!-- 项目详细信息 -->
        <el-card v-if="ticket.detailedRequirements" class="ticket-content-card glass-card">
          <div class="ticket-content">
            <h3>详细需求</h3>
            <div class="content-text">{{ ticket.detailedRequirements }}</div>
          </div>
        </el-card>

        <!-- 项目描述 -->
        <el-card class="ticket-content-card glass-card">
          <div class="ticket-content">
            <h3>项目描述</h3>
            <div class="content-text">{{ ticket.content }}</div>
          </div>
        </el-card>

        <!--
          AI分析结果 - 默认折叠。
          进详情页时这一大块会把整页顶下去，而它多数时候是空的（等生成），
          所以默认收起，点标题栏展开。折叠状态不持久化 —— 需求就是"进来默认折叠"，
          存一个 true 会让"默认"变成谎话。
        -->
        <el-card class="ai-analysis-card glass-card" :class="{ 'is-collapsed': !aiExpanded }">
          <template #header>
            <div class="ai-analysis-header" @click="aiExpanded = !aiExpanded">
              <el-icon><MagicStick /></el-icon>
              <span>AI 开发助手</span>
              <!--
                额度徽标。只在**我是这张工单的接单人**时显示 —— 后端对非接单人
                也返回 balance: 0，把它显示出来只会让人以为"我的额度是 0"。
              -->
              <span v-if="quota?.isHandler" class="quota-badge" :title="quotaTitle">
                <el-icon size="13"><Coin /></el-icon>
                剩余 {{ quota.balance }} 次
              </span>
              <el-icon class="ai-toggle-icon">
                <ArrowRight v-if="!aiExpanded" />
                <ArrowDown v-else />
              </el-icon>
              <!--
                @click.stop 是必须的：容器整体可点，不 stop 的话点「下载PRD」
                会顺手把卡片折叠起来，用户看到的是"点了下载，面板自己动了"。
              -->
              <el-button
                size="small"
                type="primary"
                @click.stop="handleDownloadPrd"
                :loading="prdLoading"
                :disabled="prdButtonDisabled"
                class="download-prd-btn"
              >
                <el-icon v-if="!prdLoading"><Download /></el-icon>
                {{ ticket.aiPrd ? '下载PRD' : '生成并下载PRD' }}
              </el-button>
            </div>
          </template>

          <!--
            用 v-if 而不是 v-show：el-tabs 的 TabNav 在 onMounted 里按 offsetWidth
            算活动下划线位置，父级 display:none 时量到 0 —— 展开后下划线会缩成 0
            宽贴在左边，要等切 tab 才自愈。v-if 在展开时于可见状态下重新挂载。
          -->
          <el-tabs v-if="aiExpanded" v-model="activeAiTab" type="border-card">
            <!--
              AI 对话排在第一个 tab。
              它是这三个功能里唯一"解锁后可以反复用"的，也是最常用的入口 ——
              排在第三个意味着每次都要先点开卡片再切一次 tab。
            -->
            <el-tab-pane label="AI对话" name="chat">
              <div class="ai-chat-container">
                <!-- 状态横幅。只在用得着解释的时候出现：
                     未解锁（说明第一次发送要花钱）或额度不足（给出会员预期）。 -->
                <div v-if="chatBanner" class="chat-banner" :class="chatBanner.type">
                  <el-icon size="14"><component :is="chatBanner.icon" /></el-icon>
                  <span>{{ chatBanner.text }}</span>
                  <el-button
                    v-if="chatBanner.type === 'no-credit'"
                    link
                    type="primary"
                    @click="goMembership"
                  >
                    了解会员
                  </el-button>
                </div>

                <AiLockedHint
                  v-if="chatLocked"
                  :title="chatLocked.title"
                  :detail="chatLocked.detail"
                  :action-text="chatLocked.actionText"
                  @action="authStore.openAuthCard('login')"
                />

                <template v-else>
                  <div class="ai-chat-messages" ref="aiChatMessagesRef">
                    <div v-for="(msg, index) in aiChatMessages" :key="index" :class="['ai-chat-message', msg.role]">
                      <div class="message-avatar">
                        <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                        <el-icon v-else><MagicStick /></el-icon>
                      </div>
                      <div class="message-content">{{ msg.content }}</div>
                    </div>
                    <!--
                      等待回复时的骨架条。模型的响应以十秒计，没有它用户分不清
                      "在等"和"没反应"—— 而且这条气泡占着位置，回复到达时是替换而不是
                      凭空多出一条，视觉上不跳。
                    -->
                    <div
                      v-if="aiChatLoading"
                      class="ai-chat-message assistant shimmer-message"
                      role="status"
                      aria-label="AI 正在回复"
                    >
                      <div class="message-avatar">
                        <el-icon><MagicStick /></el-icon>
                      </div>
                      <div class="message-content shimmer-bubble">
                        <span class="shimmer-line" style="width: 86%"></span>
                        <span class="shimmer-line" style="width: 64%"></span>
                        <span class="shimmer-line" style="width: 42%"></span>
                      </div>
                    </div>
                    <div v-if="aiChatMessages.length === 0" class="empty-chat">
                      <p>我是AI开发助手，可以帮你解答项目开发相关的问题</p>
                      <p>例如：这个项目应该怎么设计数据库？</p>
                    </div>
                  </div>
                  <div class="ai-chat-input">
                    <el-input
                      v-model="aiChatInput"
                      placeholder="输入你的问题... (Enter发送)"
                      @keyup.enter="handleAiChat"
                      :disabled="chatInputDisabled"
                    />
                    <el-button
                      type="primary"
                      :loading="aiChatLoading"
                      @click="handleAiChat"
                      :disabled="!aiChatInput.trim() || aiChatLoading || chatInputDisabled"
                    >
                      {{ chatUnlocked ? '发送' : '发送并解锁' }}
                    </el-button>
                  </div>
                </template>
              </div>
            </el-tab-pane>

            <el-tab-pane label="技术建议" name="tech">
              <!--
                `&& aiContentVisible` 不能省：内容存在不等于**该给这个人看**。
                后端把 AI 内容作为工单字段一起返回（对访客也一样），所以"访客看不到
                AI 内容"这条规则只能落在渲染条件上。
              -->
              <div v-if="ticket.aiTechSuggestion && aiContentVisible" class="ai-content markdown-content">{{ ticket.aiTechSuggestion }}</div>
              <template v-else>
                <AiLockedHint
                  v-if="analysisMask"
                  :title="analysisMask.title"
                  :detail="analysisMask.detail"
                  :action-text="analysisMask.actionText"
                  @action="authStore.openAuthCard('login')"
                />
                <div v-else class="empty-content">
                  <!--
                    走到这里 = 还没内容。两种开销状态说法不同：
                      · pairPaid：同组的另一半（很可能是 AI 对话）已经把这 1 次花掉了，
                        这次生成免费 —— 所以按钮**照旧可点**，也不需要余额。
                      · 否则：这次要花 1 次，余额不够就不给按。
                  -->
                  <p class="credit-hint">{{ pairPaid ? pairPaidHint : creditHint }}</p>
                  <el-button
                    type="primary"
                    :loading="analyzing"
                    :disabled="analyzing || analysisState === 'IN_PROGRESS' || (!pairPaid && !canAfford)"
                    @click="handleAiAnalysis"
                    class="btn-glass"
                  >
                    <el-icon v-if="!analyzing"><MagicStick /></el-icon>
                    点击生成技术建议
                  </el-button>
                </div>
              </template>
            </el-tab-pane>

            <el-tab-pane label="任务拆解" name="tasks">
              <div v-if="ticket.aiTaskBreakdown && aiContentVisible" class="ai-content markdown-content">{{ ticket.aiTaskBreakdown }}</div>
              <template v-else>
                <AiLockedHint
                  v-if="analysisMask"
                  :title="analysisMask.title"
                  :detail="analysisMask.detail"
                  :action-text="analysisMask.actionText"
                  @action="authStore.openAuthCard('login')"
                />
                <div v-else class="empty-content">
                  <!--
                    走到这里 = 还没内容。两种开销状态说法不同：
                      · pairPaid：同组的另一半（很可能是 AI 对话）已经把这 1 次花掉了，
                        这次生成免费 —— 所以按钮**照旧可点**，也不需要余额。
                      · 否则：这次要花 1 次，余额不够就不给按。
                  -->
                  <p class="credit-hint">{{ pairPaid ? pairPaidHint : creditHint }}</p>
                  <el-button
                    type="primary"
                    :loading="analyzing"
                    :disabled="analyzing || analysisState === 'IN_PROGRESS' || (!pairPaid && !canAfford)"
                    @click="handleAiAnalysis"
                    class="btn-glass"
                  >
                    <el-icon v-if="!analyzing"><MagicStick /></el-icon>
                    点击生成任务拆解
                  </el-button>
                </div>
              </template>
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <!-- 对话记录 -->
        <el-card class="comments-card glass-card">
          <template #header>
            <div class="comments-header">
              <span>对话记录</span>
              <span class="comment-count">{{ comments.length }} 条</span>
            </div>
          </template>

          <div class="comments-list">
            <div
              v-for="comment in comments"
              :key="comment.id"
              :class="['comment-item', { 'is-ai': comment.isAiSuggested, 'is-internal': comment.isInternal }]"
            >
              <div class="comment-avatar">
                <el-icon v-if="comment.isAiSuggested"><Star /></el-icon>
                <el-icon v-else><User /></el-icon>
              </div>
              <div class="comment-body">
                <div class="comment-meta">
                  <span class="comment-author">{{ comment.userName }}</span>
                  <el-tag v-if="comment.isAiSuggested" size="small" type="warning" class="glass-tag">AI 建议</el-tag>
                  <el-tag v-if="comment.isInternal" size="small" class="glass-tag">内部</el-tag>
                  <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
                </div>
                <div class="comment-content">{{ comment.content }}</div>
              </div>
            </div>

            <div v-if="comments.length === 0" class="empty-comments">
              暂无对话记录
            </div>
          </div>

          <!--
            回复输入。访客看到的是"去登录"而不是一个填了半天、点发送才被拦下来的
            输入框 —— 留言本来就要身份（后端从网关注入的 X-User-Id 取作者），
            让访客先写再被拒是最浪费他时间的一种设计。
          -->
          <div class="comment-input glass-card-static">
            <template v-if="authStore.token">
              <el-input
                v-model="newComment"
                type="textarea"
                :rows="3"
                placeholder="输入回复内容..."
                @keydown.enter.ctrl="handleAddComment"
                class="glass-input"
              />
              <div class="comment-input-actions">
                <el-checkbox v-model="isInternal" class="glass-checkbox">内部留言</el-checkbox>
                <el-button type="primary" :loading="submitting" @click="handleAddComment" class="btn-glass btn-glass-primary">
                  发送
                </el-button>
              </div>
            </template>
            <div v-else class="comment-login-hint">
              <span>登录后可以参与讨论</span>
              <el-button type="primary" size="small" class="btn-glass btn-glass-primary" @click="authStore.openAuthCard('login')">
                登录 / 注册
              </el-button>
            </div>
          </div>
        </el-card>
      </template>
    </div>

    <!-- 接单 / 去完成 / 验收：三个弹窗与需求大厅的工单卡片共用同一套组件 -->
    <PickupTicketDialog v-model="pickupVisible" :ticket="ticket || null" @done="reload" />
    <CompleteDeliveryDialog v-model="completeVisible" :ticket="ticket || null" @done="reload" />
    <ReviewDeliveryDialog v-model="reviewVisible" :ticket="ticket || null" @done="reload" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ticketApi } from '@/api'
import { useTicketStore } from '@/stores'
import { useAuthStore } from '@/stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, ArrowDown, ChatDotRound, User, Star, MagicStick, Check, Download, Finished, CircleCheck, Coin, InfoFilled, Warning } from '@element-plus/icons-vue'
import CompleteDeliveryDialog from '@/components/CompleteDeliveryDialog.vue'
import ReviewDeliveryDialog from '@/components/ReviewDeliveryDialog.vue'
import PickupTicketDialog from '@/components/PickupTicketDialog.vue'
import AiLockedHint from '@/components/AiLockedHint.vue'
import type { TicketDTO, CommentDTO, AiQuotaStatusDTO, AiFeatureState } from '@/types'
import { getStatusLabel, getStatusType, getCategoryLabel, getCategoryType } from '@/utils/ticket-labels'
import {
  AI_FEATURE_LABELS,
  MEMBERSHIP_CREDITS,
  MEMBERSHIP_DAYS,
  buildConsumeConfirmText,
  formatExpireDate
} from '@/utils/ai-quota'

const router = useRouter()
const route = useRoute()
const ticketStore = useTicketStore()
const authStore = useAuthStore()

const ticket = ref<TicketDTO>()
const comments = ref<CommentDTO[]>([])
const newComment = ref('')
const isInternal = ref(false)
const loading = ref(false)
const submitting = ref(false)
const analyzing = ref(false)
const prdLoading = ref(false)
// AI 对话排第一，默认就落在它上面（原来默认是「技术建议」）
const activeAiTab = ref('chat')
const aiExpanded = ref(false)   // AI 开发助手卡片默认折叠

// AI聊天相关
const aiChatMessages = ref<Array<{role: string, content: string}>>([])
const aiChatInput = ref('')
const aiChatLoading = ref(false)
const aiChatMessagesRef = ref<HTMLElement>()

// ---------------- AI 额度 ----------------

/**
 * 当前工单的 AI 额度状态。null = 还没拿到（未登录，或请求在飞）。
 *
 * 未登录时**不发**这个请求：/ai-usage 不在网关的匿名白名单里，发出去只会拿到
 * 401，而 401 会被 axios 拦截器当成"登录已过期"弹登录卡片 —— 访客只是打开
 * 一个工单看一眼，不该被这个他没做过的动作袭击。
 */
const quota = ref<AiQuotaStatusDTO | null>(null)

/** 访客看不到任何 AI 生成内容（AI 摘要、技术建议、任务拆解、AI 回复） */
const aiContentVisible = computed(() => !!authStore.token)

const quotaTitle = computed(() => {
  if (!quota.value) return ''
  const expire = formatExpireDate(quota.value.nextExpireAt)
  return expire
    ? `最近一批额度将于 ${expire} 过期，过期未用完将作废`
    : '当前额度为永久有效（注册赠送）'
})

async function loadAiUsage() {
  if (!ticket.value || !authStore.token) {
    quota.value = null
    return
  }
  try {
    quota.value = await ticketApi.getAiUsage(ticket.value.id)
  } catch {
    // 拉不到额度信息不该把页面搞崩：三个功能会退化成"用不了"，但详情照常可读。
    // （这里不再弹提示 —— 用户对此无能为力，弹窗只会增加噪音。）
    quota.value = null
  }
}

/** 各功能状态。quota 未就绪时一律按 NOT_HANDLER 处理，宁可少露按钮也不多露。 */
const analysisState = computed<AiFeatureState>(() => quota.value?.analysis ?? 'NOT_HANDLER')
const chatState = computed<AiFeatureState>(() => quota.value?.chat ?? 'NOT_HANDLER')

/**
 * "AI 分析与对话"这一格是否已经付过。后端把这一格的同一次消耗同时记在 analysis 与
 * chat 两个字段上，所以两者必然相等；这里用 `||` 而不是取其一，是免得以后谁只改了
 * 一边就悄悄改变了判定。
 *
 * ⚠️ **USED 不等于"不能用了"**。合并之后它只说明这一格的开销已经结清：两个功能谁
 * 还没产出过内容，谁就能再跑一次、不再扣费。所以它决定的是"要不要弹确认框、还要不要
 * 余额"，不是"按钮能不能按"——判断能否运行要看具体那个功能有没有内容，见模板。
 */
const pairPaid = computed(() => analysisState.value === 'USED' || chatState.value === 'USED')

/** AI 对话能不能直接发。这一格付过了就解锁了，与余额无关 */
const chatUnlocked = pairPaid

const prdState = computed<AiFeatureState>(() => quota.value?.prd ?? 'NOT_HANDLER')

/** 已经生成过 PRD → 重复下载命中后端存量，不扣费，任何状态下都不用拦 */
const prdDownloadable = computed(() => !!ticket.value?.aiPrd)

/**
 * 余额还够不够付一次。
 *
 * 后端返回的功能状态（AVAILABLE/USED/…）**不含余额维度** —— 余额为 0 时三个功能
 * 仍是 AVAILABLE，要等你按下去了才回 402。所以"余额不足就置灰"这条只能在前端加，
 * 否则用户会看到一个可点的按钮，点完只得到一句错误。
 */
const canAfford = computed(() => (quota.value?.balance ?? 0) > 0)

const prdButtonDisabled = computed(() => {
  if (prdLoading.value) return true
  // 存量下载永远可用
  if (prdDownloadable.value) return false
  // 非接单人（含访客）：不置灰，让他点了能看到原因 / 登录卡片。
  // 置灰的按钮什么也不说，是最糟糕的一种"不可用"。
  if (!quota.value?.isHandler) return false
  if (prdState.value !== 'AVAILABLE') return true
  return !canAfford.value
})

/** 技术建议 / 任务拆解两个 tab 共用的遮罩（要显示遮罩时返回 null） */
const analysisMask = computed(() => {
  if (!authStore.token) {
    return { title: '登录后可用', detail: 'AI 辅助开发只对登录用户开放，注册即送 5 次额度。', actionText: '去登录' }
  }
  if (!quota.value) {
    return { title: '正在加载额度信息…', detail: '', actionText: '' }
  }
  if (!quota.value.isHandler) {
    return {
      title: '接单后可用',
      detail: ticket.value?.handlerId
        ? '本工单已由其他开发者接单。AI 辅助开发只对这张工单的接单人开放。'
        : '本工单还没有接单人。接单之后，AI 辅助开发就会对你开放。',
      actionText: ''
    }
  }
  return null
})

/** 生成一次消耗多少、还剩多少 —— 按钮上方那行小字（只在**这一格还没付过**时显示） */
const creditHint = computed(() => {
  if (!quota.value?.isHandler) return ''
  if (analysisState.value === 'IN_PROGRESS') return '上一次调用还在进行中，请稍候…'
  if (quota.value.balance <= 0) {
    return `额度不足。开通会员可获得 ${MEMBERSHIP_CREDITS} 次（${MEMBERSHIP_DAYS} 天有效）`
  }
  const expire = formatExpireDate(quota.value.nextExpireAt)
  return `AI 分析与 AI 对话合并计一次，PRD 另计一次，每张工单共 2 次。本次消耗 1 次（剩余 ${quota.value.balance} 次${expire ? `，最近 ${expire} 过期` : ''}）`
})

/**
 * 这一格已经付过、但内容还没生成时显示的那句话。
 *
 * 只在"分析与对话"这一格用得上：同组的另一半先把这 1 次花掉了，所以这次生成免费。
 * PRD 是独立一格，不存在这种状态（它没内容就是没生成过）。
 */
const pairPaidHint = 'AI 分析与 AI 对话合并计一次，这一次已经付过了，本次生成不再消耗额度。'

/** AI 对话输入框能不能打字：访客与非接单人都不能，接单人（未解锁也行）可以 */
const chatInputDisabled = computed(() => {
  if (!authStore.token) return true
  if (!quota.value) return true
  if (!quota.value.isHandler || chatState.value === 'IN_PROGRESS') return true
  // 已解锁的对话不再扣费，余额为 0 也照常能聊；只有"还没解锁"才需要余额。
  return !chatUnlocked.value && !canAfford.value
})

/** 聊天 tab 用不了时的遮罩 */
const chatLocked = computed(() => {
  if (!authStore.token) {
    return { title: '登录后可用', detail: '登录并接单后，可以用 1 次额度解锁本工单的 AI 对话与 AI 分析。', actionText: '去登录' }
  }
  if (!quota.value) {
    return { title: '正在加载额度信息…', detail: '', actionText: '' }
  }
  if (!quota.value.isHandler) {
    return {
      title: '接单后可用',
      detail: ticket.value?.handlerId
        ? '本工单已由其他开发者接单。AI 对话只对这张工单的接单人开放。'
        : '本工单还没有接单人。接单之后即可解锁 AI 对话。',
      actionText: ''
    }
  }
  if (chatState.value === 'IN_PROGRESS') {
    return { title: '正在处理中', detail: '上一次调用还在进行，完成后本工单的 AI 对话就会解锁。', actionText: '' }
  }
  return null
})

/**
 * 聊天框顶部的状态横幅。只在有必要解释的时候出现，避免变成一个常驻的说明条。
 *   未解锁 → 说清"第一条消息要花 1 次，之后免费"，并且要说清它顺手解锁了 AI 分析
 *            （用户按的是一次，不该事后才发现还白得了另一件东西，也不该以为花了两次）
 *   解锁了但余额为 0 → 给会员预期（已解锁的对话不受余额影响，所以只是提示）
 */
const chatBanner = computed(() => {
  if (!authStore.token || !quota.value?.isHandler) return null
  if (chatState.value === 'IN_PROGRESS') return null
  if (!chatUnlocked.value) {
    if (quota.value.balance <= 0) {
      return {
        type: 'no-credit',
        icon: Warning,
        text: `额度不足，无法解锁「AI 分析与对话」。开通会员可获得 ${MEMBERSHIP_CREDITS} 次（${MEMBERSHIP_DAYS} 天有效）。`
      }
    }
    return {
      type: 'info',
      icon: InfoFilled,
      text: `本工单的「AI 分析与对话」尚未解锁：发送第一条消息将消耗 1 次额度，之后本工单内可无限对话，AI 分析也一并可用（这一对共用这 1 次）。`
    }
  }
  return {
    type: 'unlocked',
    icon: Check,
    text: '「AI 分析与对话」已解锁（两者共用这 1 次额度），本工单内可无限提问，不再消耗额度。'
  }
})

// ---------------- AI 动作的公共前置 ----------------

/** axios 的原始错误（网络/超时/5xx）。业务错误被拦截器转成了普通 Error，不带这个标记 */
function isAxiosError(e: any): boolean {
  return !!e?.isAxiosError
}

/**
 * 去会员页。此前这里是一句"会员功能即将上线，敬请期待"—— 页面上「了解会员」的按钮
 * 点了什么都不会发生（只弹一条提示），因为那时确实没有会员页。现在有了。
 */
function goMembership() {
  router.push('/membership')
}

/**
 * 三个 AI 动作的公共前置。返回 true 表示可以继续走确认框。
 *
 * 这些判断后端**全都再做一遍**，这里只是为了让用户不必先失败一次才知道原因 ——
 * 前端永远不是权限边界。
 */
function aiPrecheck(state: AiFeatureState, label: string): boolean {
  if (!authStore.token) {
    authStore.openAuthCard('login')
    return false
  }
  if (!quota.value) {
    ElMessage.warning('额度信息还在加载，请稍候再试')
    return false
  }
  if (!quota.value.isHandler) {
    ElMessage.warning(ticket.value?.handlerId ? '本工单已由其他开发者接单，只有接单人可以使用 AI 功能' : '接单后才可以使用 AI 功能')
    return false
  }
  if (state === 'USED') {
    // 合并之后"分析与对话"这半边的 USED 走不到这里 —— 调用方会先看到 pairPaid 而跳过
    // 整个前置（那一次不用再花钱）。能撞到这句的实际上只剩 PRD。
    ElMessage.warning(`该工单的「${label}」已经用过了`)
    return false
  }
  if (state === 'IN_PROGRESS') {
    ElMessage.warning('上一次调用还在进行中，请稍候再试')
    return false
  }
  if (quota.value.balance <= 0) {
    ElMessage.error('AI 辅助开发额度不足')
    // 直接带他去会员页：这里除了"额度不够"没有别的出路，停在原地说一句
    // "额度不足"然后什么都不做，等于让他自己去猜哪里能充。
    goMembership()
    return false
  }
  return true
}

/** 消耗前的确认框。返回 false = 用户点了"再想想" */
async function confirmConsume(label: string, isChat: boolean): Promise<boolean> {
  const balance = quota.value?.balance ?? 0
  // 分析与对话共用这 1 次，花在哪一边都要把"另一边也解锁了"说清楚 —— 否则用户
  // 事后发现自己"白得了"一件东西，第一反应是怀疑哪里算错了。
  const pairNote = label === AI_FEATURE_LABELS.ANALYSIS
    ? '「AI 分析与 AI 对话」共用这 1 次，AI 对话也会一并解锁。'
    : ''
  const text = isChat
    ? `本次将消耗 1 次 AI 辅助开发额度（当前剩余 ${balance} 次）。解锁后本工单内可无限对话，并同时解锁 AI 分析（这一对共用这 1 次），不再扣费。确认解锁？`
    : buildConsumeConfirmText(label, balance, quota.value?.nextExpireAt, pairNote)
  try {
    await ElMessageBox.confirm(text, '消耗额度确认', {
      confirmButtonText: '确认使用',
      cancelButtonText: '再想想',
      type: 'warning'
    })
    return true
  } catch {
    return false
  }
}


// ---------------- 角色与状态判定 ----------------

const myId = computed(() => authStore.userInfo?.id)

/**
 * 接单入口：没人接 + 状态为 OPEN + 我不是发布者 + 我**不是发布者身份**。
 * 四者缺一不可。
 *
 * 最后一条是身份层面的：发布者账号在界面上不提供接单这件事（他发需求，不接活）。
 * 前三条是"这一单里我站哪边"，第四条是"我这个账号是干嘛的"—— 两者是不同维度，
 * 所以 `creatorId !== 我` 挡不住"发布者去接别人发的单"，得单独判 role。
 *
 * 这条只是前端不给入口。后端 POST /pickup 现在也要求登录（缺 X-User-Id 报 401），
 * 但仍然**不校验身份** —— 它只挡"不能接自己的工单"。这是有意的：Role 的定位是
 * "决定默认给你看什么"，不是"把账号锁死在一边"。编辑/删除/关闭/重开/交付/验收
 * 六件事已经在 TicketService 里按"这一单里我站哪边"做了硬校验
 * （requireCreator / requireHandler），接单这条角色策略仍留待以后。
 */
const canPickup = computed(
  () =>
    !!ticket.value &&
    !ticket.value.handlerId &&
    ticket.value.status === 'OPEN' &&
    ticket.value.creatorId !== myId.value &&
    authStore.userInfo?.role !== 'PUBLISHER'
)

const isCreator = computed(() => !!ticket.value && ticket.value.creatorId === myId.value)
const isHandler = computed(() => !!ticket.value && ticket.value.handlerId === myId.value)

/** 归档态：验收通过后私聊记录已被删除，聊天入口要跟着消失 */
const isArchived = computed(
  () => !!ticket.value && ['COMPLETED', 'CLOSED'].includes(ticket.value.status)
)

/**
 * 编辑入口：我是发布者 + 工单未完结。
 *
 * 与后端 updateTicket 的两道门一一对应（requireCreator 的 403 + 未完结的 400），
 * 但这里只是不把入口露出来 —— 真正的边界在服务端。
 * 复用 isArchived 而不是再写一遍状态数组：这个文件里"已完结"只有一份定义，
 * 两份必然漂移（后端的孪生是 TicketService.ARCHIVED_STATUSES）。
 */
const canEdit = computed(() => isCreator.value && !isArchived.value)

async function loadTicket() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    ticket.value = await ticketApi.getTicket(id)
    comments.value = ticket.value.comments || []
  } finally {
    loading.value = false
  }
  // 额度状态依赖工单（判断我是不是接单人），所以放在工单之后拉。
  // 两个请求串行是有意的：并行的话 AI 卡片会先按"未知身份"渲染一次再跳变。
  await loadAiUsage()
}

// ---------------- 接单 ----------------

// 接单本身（确认框 + 调接口 + 刷新）已抽到 components/PickupTicketDialog.vue，
// 因为需求大厅的工单卡片上也要有同一个入口 —— 两份确认框必然漂移。
const pickupVisible = ref(false)

function openPickupDialog() {
  // 访客点「接单」→ 开登录卡片（与大厅的 openPickup 同一处理）。
  // canPickup 对访客为真是有意的：让他看见"这里可以接单"，比藏起来更能促成注册。
  if (!authStore.token) {
    authStore.openAuthCard('login')
    return
  }
  pickupVisible.value = true
}

// ---------------- 去完成 / 验收 ----------------

const completeVisible = ref(false)
const reviewVisible = ref(false)

function openCompleteDialog() {
  completeVisible.value = true
}

function openReviewDialog() {
  reviewVisible.value = true
}

function reload() {
  loadTicket()
  ticketStore.triggerRefresh()
}

/**
 * AI 分析（技术建议 + 任务拆解）。
 *
 * 一次消耗同时产出两个 tab 的内容 —— 后端那边也是一个槽位，所以两个 tab 的按钮
 * 共用一个 analyzing，一个在飞时另一个也禁用，避免用户以为"再点一次能生成另一半"。
 *
 * pairPaid 时这一格已经由同组的另一半（AI 对话）付过了：这次生成免费，不弹确认框，
 * 也不看余额 —— 后端走"不再扣费"那条路。前置校验整段跳过是安全的：pairPaid 蕴含
 * 已登录、是接单人、quota 已就绪（否则状态不可能是 USED）。
 */
async function handleAiAnalysis() {
  if (!ticket.value || analyzing.value) return
  if (!pairPaid.value) {
    if (!aiPrecheck(analysisState.value, AI_FEATURE_LABELS.ANALYSIS)) return
    if (!(await confirmConsume(AI_FEATURE_LABELS.ANALYSIS, false))) return
  }

  analyzing.value = true
  try {
    ticket.value = await ticketApi.analyzeTicket(ticket.value.id)
    ElMessage.success('AI 分析完成')
    await loadAiUsage()
  } catch (e) {
    // 业务错误（已用过 / 额度不足 / 非接单人）的提示由 axios 拦截器弹过，
    // 这里只兜网络与超时 —— 否则用户会连着看到两条意思一样的红字。
    if (isAxiosError(e)) ElMessage.error('AI 分析失败，请稍后重试')
  } finally {
    analyzing.value = false
  }
}

/**
 * 生成并下载 PRD。
 *
 * 三种情况下**不需要消耗额度**，因此也跳过确认框：
 *   1. 工单上已有存量 PRD（后端命中存量直接返回，不扣费）；
 *   2. 用户点的是"再下一份文件"这个动作本身。
 * 只有"第一次生成"才走前置校验 + 确认。
 */
async function handleDownloadPrd() {
  if (!ticket.value || prdLoading.value) return

  const needGenerate = !prdDownloadable.value
  if (needGenerate) {
    if (!aiPrecheck(prdState.value, AI_FEATURE_LABELS.PRD)) return
    if (!(await confirmConsume(AI_FEATURE_LABELS.PRD, false))) return
  }

  prdLoading.value = true
  try {
    // 拦截器对 blob 请求直接返回 Blob 本身，这里不能再取 response.data
    // （Blob 没有 .data，取到的是 undefined，下载出来的文件内容就是字符串
    //  "undefined"）。
    const blob = await ticketApi.downloadPrd(ticket.value.id)
    // 拦截器在运行时返回的是 Blob，但它的静态返回类型仍标注为 AxiosResponse，
    // 所以这里要经 unknown 中转一次才能断言。
    const url = window.URL.createObjectURL(blob as unknown as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `PRD_${ticket.value.title}.md`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('PRD 下载成功')
    // 生成路径要刷新额度与工单（aiPrd 已落库，按钮文案随之变成"下载PRD"）
    if (needGenerate) {
      await loadTicket()
      ticketStore.triggerRefresh()
    }
  } catch (e) {
    if (isAxiosError(e)) ElMessage.error('下载失败，请稍后重试')
  } finally {
    prdLoading.value = false
  }
}

/**
 * AI 对话。**首条消息消耗 1 次额度，解锁"AI 分析与对话"这一格**（后端语义）：
 * 之后本工单内无限对话，AI 分析也一并可用。
 *
 * 所以确认框只在"这一格还没付过"时弹：已付过还每次弹一次，用户会以为每条消息都在扣费。
 * 反过来，如果同组的 AI 分析先付过了，这里的 chatUnlocked 也是 true —— 不需要再花钱，
 * 也就不该弹框，这正是"谁先用谁扣、另一个免费"在前端的落点。
 *
 * @param ev 键盘事件（点按钮时是 MouseEvent，上面没有 isComposing，取到 undefined 即没在组字）
 */
async function handleAiChat(ev?: Event) {
  // 中文输入法组字期间的回车是"选中候选词"，不是"发送"。不挡掉的话，打一半拼音
  // 按回车选词就会把那串拼音发出去 —— 这是中文用户必然会踩到的一次。
  if (ev && (ev as KeyboardEvent).isComposing) return
  if (!ticket.value || !aiChatInput.value.trim() || aiChatLoading.value) return

  if (!chatUnlocked.value) {
    if (!aiPrecheck(chatState.value, AI_FEATURE_LABELS.CHAT)) return
    if (!(await confirmConsume(AI_FEATURE_LABELS.CHAT, true))) return
  }

  const userMessage = aiChatInput.value.trim()
  aiChatMessages.value.push({ role: 'user', content: userMessage })
  aiChatInput.value = ''
  aiChatLoading.value = true
  // 自己那条 + 骨架条都要立刻可见。回复要等十几秒，不滚的话用户在自己的消息
  // 都还没进视野的时候就开始等，会以为没发出去。
  await scrollAiChatToBottom()

  try {
    const response = await ticketApi.aiChat(ticket.value.id, userMessage)
    aiChatMessages.value.push({ role: 'assistant', content: response.content })
    await scrollAiChatToBottom()
    // 解锁这一步改变了 quota.chat（AVAILABLE → USED），横幅、按钮文案、
    // 剩余次数徽标都得跟着更新
    if (!chatUnlocked.value) await loadAiUsage()
  } catch (err) {
    if (isAxiosError(err)) {
      ElMessage.error('AI 回复失败，请稍后重试')
    } else {
      // 业务错误（未解锁 / 额度不足 / 不是接单人）：撤掉那条永远不会有回答的气泡，
      // 并把问题**还回输入框** —— 用户刚打的一段字不能就这么没了。
      aiChatMessages.value.pop()
      aiChatInput.value = userMessage
    }
  } finally {
    aiChatLoading.value = false
  }
}

/** 消息区滚到底。骨架条出现与回复到达都要各调一次（两者都在 DOM 更新之后） */
async function scrollAiChatToBottom() {
  await nextTick()
  if (aiChatMessagesRef.value) {
    aiChatMessagesRef.value.scrollTop = aiChatMessagesRef.value.scrollHeight
  }
}

async function handleAddComment() {
  if (!ticket.value || !newComment.value.trim()) return
  submitting.value = true
  try {
    const comment = await ticketApi.addComment(ticket.value.id, {
      content: newComment.value.trim(),
      isInternal: isInternal.value
    })
    comments.value.push(comment)
    newComment.value = ''
    ticketStore.triggerRefresh()
  } finally {
    submitting.value = false
  }
}

function formatDate(date: string) {
  return new Date(date).toLocaleString('zh-CN')
}

function getPriorityType(priority: string) {
  const map: Record<string, string> = {
    P0: 'danger',
    P1: 'warning',
    P2: 'primary',
    P3: 'info'
  }
  return map[priority] || 'info'
}

onMounted(() => {
  loadTicket()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.proof-link {
  color: var(--accent-info);
  text-decoration: none;
  word-break: break-all;
}

.proof-link:hover {
  text-decoration: underline;
}

/* 此处原有 .summary / .summary-row / .summary-label / .summary-value /
   .archive-note / .pickup-confirm 六条 —— 只服务于已抽走的接单弹窗。
   那套样式在两个交付弹窗里各有一份、这里曾是第三份，跟着弹窗一起删掉。 */

.ticket-header-card {
  margin-bottom: 20px;
}

.ticket-header {
  margin-bottom: 20px;
}

.ticket-title-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.ticket-id {
  font-size: 14px;
  color: var(--text-tertiary);
  font-weight: 500;
}

.ticket-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.ticket-tags {
  display: flex;
  gap: 10px;
}

.ticket-meta {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  padding: 20px 0;
  border-top: 1px solid var(--glass-border);
  border-bottom: 1px solid var(--glass-border);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-item .label {
  font-size: 12px;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.meta-item span:last-child {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

.tech-stack {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--glass-border);
}

.tech-stack .label {
  font-size: 12px;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
  display: block;
}

.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-summary {
  margin-top: 20px;
  padding: 16px;
  border-radius: var(--radius-md) !important;
}

.ai-summary-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--accent-info);
}

.ai-summary-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.ticket-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}

.ticket-content-card {
  margin-bottom: 20px;
}

.ticket-content h3 {
  font-size: 12px;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

.content-text {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  color: var(--text-secondary);
}

.ai-analysis-card {
  margin-bottom: 20px;
}

.ai-analysis-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--accent-warning);
  /* 整个标题栏是折叠开关 */
  cursor: pointer;
  user-select: none;
}

.ai-toggle-icon {
  font-size: 14px;
  color: var(--text-tertiary);
}

/* 剩余额度徽标。只在我是这张工单的接单人时出现（后端对非接单人返回 0）。 */
.quota-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 6px;
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent-warning);
  background: rgba(255, 159, 10, 0.14);
  border: 1px solid rgba(255, 159, 10, 0.32);
  border-radius: 999px;
  cursor: help;
}

/* 折叠时把 body 一起收掉：只用 v-if 拿掉 el-tabs 的话，空 body 的默认
   padding 会在标题下面留一条空白。 */
.ai-analysis-card.is-collapsed :deep(.el-card__body) {
  display: none;
}

.download-prd-btn {
  margin-left: auto;
}

/* AI聊天样式 */
.ai-chat-container {
  display: flex;
  flex-direction: column;
  height: 400px;
}

/*
 * 聊天框顶部的状态横幅（未解锁 / 已解锁 / 额度不足）。
 * 只在需要解释的时候渲染，所以不给它留常驻高度。
 */
.chat-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 9px 12px;
  font-size: 13px;
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  color: var(--text-secondary);
}

.chat-banner.info {
  color: var(--accent-info);
  border-color: rgba(100, 210, 255, 0.32);
  background: rgba(100, 210, 255, 0.1);
}

.chat-banner.no-credit {
  color: var(--accent-warning);
  border-color: rgba(255, 159, 10, 0.32);
  background: rgba(255, 159, 10, 0.1);
}

.chat-banner.unlocked {
  color: var(--accent-success);
  border-color: rgba(48, 209, 88, 0.3);
  background: rgba(48, 209, 88, 0.1);
}

/* 按钮上方那行"要花几次"的小字 */
.credit-hint {
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--text-tertiary);
}

.ai-chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
}

.ai-chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.ai-chat-message.user {
  flex-direction: row-reverse;
}

.ai-chat-message.user .message-content {
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
  color: #fff;
}

.ai-chat-message .message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.ai-chat-message.assistant .message-avatar {
  background: linear-gradient(135deg, var(--accent-warning), var(--accent-danger));
}

.ai-chat-message .message-content {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: var(--glass-bg);
  font-size: 14px;
  line-height: 1.6;
}

.empty-chat {
  text-align: center;
  color: var(--text-tertiary);
  padding: 32px;
}

/*
  等待回复时的骨架条。高光用 --glass-bg → --glass-bg-active 这一对既有变量，
  不写死颜色，深浅主题都不用另配。
*/
.shimmer-bubble {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 200px;
  /* 底色交给里面的横杠，自己不要 —— 灰杠浮在灰底上就看不出是骨架了 */
  background: transparent;
}

.shimmer-line {
  display: block;
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(
    90deg,
    var(--glass-bg) 25%,
    var(--glass-bg-active) 37%,
    var(--glass-bg) 63%
  );
  background-size: 400% 100%;
  animation: ai-chat-shimmer 1.4s ease-in-out infinite;
}

@keyframes ai-chat-shimmer {
  from {
    background-position: 100% 50%;
  }
  to {
    background-position: 0 50%;
  }
}

/* 系统开了"减少动态效果"就只留静态横杠：这时候一条一直在扫的光比没有还烦 */
@media (prefers-reduced-motion: reduce) {
  .shimmer-line {
    animation: none;
  }
}

.empty-chat p {
  margin: 8px 0;
}

.ai-chat-input {
  display: flex;
  gap: 12px;
}

.ai-content {
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  color: var(--text-secondary);
  padding: 16px;
  background: var(--glass-bg);
  border-radius: var(--radius-md);
}

.empty-content {
  text-align: center;
  padding: 32px;
  color: var(--text-tertiary);
}

.comments-card {
  margin-bottom: 20px;
}

.comments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.comment-count {
  font-size: 12px;
  color: var(--text-tertiary);
}

.comments-list {
  max-height: 400px;
  overflow-y: auto;
  margin-bottom: 20px;
}

.comment-item {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--glass-border);
}

.comment-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.comment-item.is-ai .comment-avatar {
  background: linear-gradient(135deg, var(--accent-warning), var(--accent-danger));
}

.comment-item.is-internal {
  opacity: 0.7;
}

.comment-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.comment-author {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.comment-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.comment-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.empty-comments {
  text-align: center;
  padding: 48px;
  color: var(--text-tertiary);
}

.comment-input {
  padding: 16px;
  border-radius: var(--radius-md) !important;
}

.comment-input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

/* 访客看到的"登录后参与讨论"，代替输入框 */
.comment-login-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 4px;
  font-size: 14px;
  color: var(--text-tertiary);
}

.btn-glass-success {
  background: linear-gradient(135deg, var(--accent-success), #7dffb3) !important;
}
</style>
