<template>
  <div class="ticket-chat">
    <div class="page-header">
      <el-button text @click="router.push(`/tickets/${ticketId}`)" class="btn-glass">
        <el-icon><ArrowLeft /></el-icon> 返回工单
      </el-button>
      <h2>项目沟通</h2>
    </div>

    <div v-loading="loading" class="chat-container">
      <!--
        私密沟通：只有发布者和接单者能看。
        边界在服务端（两个 /chat 端点都走 TicketService.requireParticipantTicket，
        非参与者 403）。这张卡片是"直接敲 URL 进来"时的兜底 —— 正常使用中入口
        已经被 TicketDetail.vue 收紧了，走不到这里。
      -->
      <el-card v-if="ticket && !isParticipant" class="glass-card-static">
        <el-empty description="这是发布者与接单者之间的私密沟通，你不在其中" />
      </el-card>

      <template v-if="ticket && isParticipant">
        <!-- 工单信息卡片 -->
        <el-card class="ticket-info-card glass-card">
          <div class="ticket-info">
            <span class="ticket-title">{{ ticket.title }}</span>
            <div class="ticket-meta">
              <el-tag size="small" :type="getStatusType(ticket.status)">{{ getStatusLabel(ticket.status) }}</el-tag>
              <span class="separator">|</span>
              <span>发布者: {{ ticket.creatorName }}</span>
              <span v-if="ticket.handlerName" class="separator">|</span>
              <span v-if="ticket.handlerName">接单者: {{ ticket.handlerName }}</span>
            </div>
          </div>
        </el-card>

        <!-- 聊天消息列表 -->
        <el-card class="chat-messages-card glass-card">
          <div class="messages-list" ref="messagesListRef">
            <div
              v-for="msg in messages"
              :key="msg.id"
              :class="['message-item', { 'is-mine': msg.userId === currentUserId, 'is-ai': msg.senderType === 'AI' }]"
            >
              <div class="message-avatar">
                <el-icon v-if="msg.senderType === 'AI'"><Monitor /></el-icon>
                <el-icon v-else><User /></el-icon>
              </div>
              <div class="message-content">
                <div class="message-header">
                  <span class="message-sender">{{ msg.userName }}</span>
                  <el-tag v-if="msg.senderType === 'AI'" size="small" type="warning" class="glass-tag">AI</el-tag>
                  <span class="message-time">{{ formatTime(msg.createdAt) }}</span>
                </div>
                <div class="message-text">{{ msg.content }}</div>
              </div>
            </div>

            <div v-if="messages.length === 0" class="empty-messages">
              <el-icon><ChatDotRound /></el-icon>
              <p>暂无消息，开始沟通吧！</p>
            </div>
          </div>
        </el-card>

        <!-- 消息输入框 -->
        <el-card class="chat-input-card glass-card">
          <div class="input-area">
            <el-input
              v-model="newMessage"
              type="textarea"
              :rows="3"
              placeholder="输入消息... (Ctrl+Enter 发送)"
              @keydown.enter.ctrl="handleSendMessage"
              class="glass-input"
            />
            <div class="input-actions">
              <el-button
                type="primary"
                :loading="sending"
                :disabled="!newMessage.trim()"
                @click="handleSendMessage"
                class="btn-glass btn-glass-primary"
              >
                发送
              </el-button>
            </div>
          </div>
        </el-card>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ticketApi } from '@/api'
import { ElMessage } from 'element-plus'
import { ArrowLeft, User, Monitor, ChatDotRound } from '@element-plus/icons-vue'
import { getStatusLabel, getStatusType } from '@/utils/ticket-labels'
import { useAuthStore } from '@/stores/auth'
import type { TicketDTO, ChatMessageDTO } from '@/types'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const ticketId = Number(route.params.id)
const ticket = ref<TicketDTO>()
const messages = ref<ChatMessageDTO[]>([])
const newMessage = ref('')
const loading = ref(false)
const sending = ref(false)
const messagesListRef = ref<HTMLElement>()

// 与 TicketDetail.vue 的 myId 读同一个来源（authStore.userInfo.id）。
// 此前读的是 localStorage 的 userId 且缺值时**默认成 1** —— 那个兜底会把任意一个人
// 判成"用户 1"，于是 isParticipant 变真、请求发出去、被后端 403 挡回来。
const currentUserId = computed(() => authStore.userInfo?.id)

/**
 * 只有发布者与接单者属于这次沟通。
 *
 * 这是**展示层**的判定：真正的边界在后端（TicketController 两个 /chat 端点都走
 * TicketService.requireParticipantTicket）。这里的作用是别让非参与者白打接口 ——
 * 后端会 403，而 axios 拦截器在组件的 catch 之前就弹了红条，本页又是 5 秒一次的
 * 轮询，不拦就是每 5 秒弹一条。
 */
const isParticipant = computed(() => {
  if (!ticket.value) return false
  const me = currentUserId.value
  if (me === undefined || me === null) return false
  return ticket.value.creatorId === me || ticket.value.handlerId === me
})

async function loadTicket() {
  loading.value = true
  try {
    ticket.value = await ticketApi.getTicket(ticketId)
  } finally {
    loading.value = false
  }
}

async function loadMessages() {
  // 非参与者一个请求都不发（理由见 isParticipant 上方）。直接敲 URL 进来的人
  // 会看到下面那张"你不在其中"的卡片，而不是一串红条。
  if (!isParticipant.value) return
  try {
    messages.value = await ticketApi.getChatHistory(ticketId)
    await nextTick()
    scrollToBottom()
  } catch {
    // ignore
  }
}

async function handleSendMessage() {
  if (!newMessage.value.trim() || sending.value) return

  sending.value = true
  try {
    const msg = await ticketApi.sendChatMessage(ticketId, {
      content: newMessage.value.trim()
    })
    messages.value.push(msg)
    newMessage.value = ''
    await nextTick()
    scrollToBottom()
  } catch {
    ElMessage.error('发送失败')
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
  if (messagesListRef.value) {
    messagesListRef.value.scrollTop = messagesListRef.value.scrollHeight
  }
}

function formatTime(date: string) {
  return new Date(date).toLocaleString('zh-CN')
}

// 定时刷新消息
let refreshTimer: ReturnType<typeof setInterval>

onMounted(async () => {
  // 必须等工单回来：isParticipant 要拿 creatorId/handlerId 才判得了，
  // 不等的话首次 loadMessages 会因为 ticket 还是空而直接早退，白错过一次刷新。
  await loadTicket()
  loadMessages()
  // 每5秒刷新消息
  refreshTimer = setInterval(loadMessages, 5000)
})

// 组件卸载时清除定时器
import { onUnmounted } from 'vue'
onUnmounted(() => {
  clearInterval(refreshTimer)
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.chat-container {
  max-width: 900px;
  margin: 0 auto;
}

.ticket-info-card {
  margin-bottom: 16px;
}

.ticket-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ticket-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.ticket-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-secondary);
}

.separator {
  color: var(--text-tertiary);
}

.chat-messages-card {
  margin-bottom: 16px;
  min-height: 400px;
}

.messages-list {
  max-height: 500px;
  overflow-y: auto;
  padding: 16px;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.message-item.is-mine {
  flex-direction: row-reverse;
}

.message-item.is-mine .message-content {
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
  color: #fff;
}

.message-item.is-mine .message-header {
  flex-direction: row-reverse;
}

.message-item.is-mine .message-sender,
.message-item.is-mine .message-time {
  color: rgba(255, 255, 255, 0.8);
}

.message-avatar {
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

.message-item.is-ai .message-avatar {
  background: linear-gradient(135deg, var(--accent-warning), var(--accent-danger));
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: var(--glass-bg);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.message-sender {
  font-weight: 600;
  font-size: 13px;
  color: var(--text-primary);
}

.message-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.message-text {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.empty-messages {
  text-align: center;
  padding: 48px;
  color: var(--text-tertiary);
}

.empty-messages .el-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.chat-input-card {
  margin-bottom: 16px;
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
