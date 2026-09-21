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
              <span>{{ ticket.acceptedByName || '待接单' }}</span>
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

          <!-- AI 摘要 -->
          <div v-if="ticket.aiSummary" class="ai-summary glass-card-static">
            <div class="ai-summary-header">
              <el-icon><ChatDotRound /></el-icon>
              <span>AI 摘要</span>
            </div>
            <div class="ai-summary-content">{{ ticket.aiSummary }}</div>
          </div>

          <!-- 操作按钮 -->
          <div class="ticket-actions">
            <!-- 接单按钮 - 只有非发布者且工单未接单时显示 -->
            <el-button
              v-if="!ticket.acceptedBy && ticket.status === 'OPEN'"
              type="success"
              @click="handlePickup"
              class="btn-glass btn-glass-success"
            >
              <el-icon><Check /></el-icon> 接单
            </el-button>

            <!-- 进入聊天 - 接单后显示 -->
            <el-button
              v-if="ticket.acceptedBy"
              type="primary"
              @click="router.push(`/tickets/${ticket.id}/chat`)"
              class="btn-glass btn-glass-primary"
            >
              <el-icon><ChatDotRound /></el-icon> 进入聊天
            </el-button>

            <el-button @click="router.push(`/tickets/${ticket.id}/edit`)" class="btn-glass">
              编辑
            </el-button>

            <el-button
              v-if="ticket.status === 'OPEN'"
              type="warning"
              :loading="analyzing"
              @click="handleAiAnalysis"
              class="btn-glass"
            >
              <el-icon v-if="!analyzing"><MagicStick /></el-icon>
              AI开发分析
            </el-button>
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

        <!-- AI分析结果 -->
        <el-card class="ai-analysis-card glass-card">
          <template #header>
            <div class="ai-analysis-header">
              <el-icon><MagicStick /></el-icon>
              <span>AI 开发助手</span>
              <el-button
                size="small"
                type="primary"
                @click="handleDownloadPrd"
                class="download-prd-btn"
              >
                <el-icon><Download /></el-icon> 下载PRD
              </el-button>
            </div>
          </template>

          <el-tabs v-model="activeAiTab" type="border-card">
            <el-tab-pane label="技术建议" name="tech">
              <div v-if="ticket.aiTechSuggestion" class="ai-content markdown-content">{{ ticket.aiTechSuggestion }}</div>
              <div v-else class="empty-content">
                <el-button type="primary" :loading="analyzing" @click="handleAiAnalysis" class="btn-glass">
                  <el-icon v-if="!analyzing"><MagicStick /></el-icon>
                  点击生成技术建议
                </el-button>
              </div>
            </el-tab-pane>
            <el-tab-pane label="任务拆解" name="tasks">
              <div v-if="ticket.aiTaskBreakdown" class="ai-content markdown-content">{{ ticket.aiTaskBreakdown }}</div>
              <div v-else class="empty-content">
                <el-button type="primary" :loading="analyzing" @click="handleAiAnalysis" class="btn-glass">
                  <el-icon v-if="!analyzing"><MagicStick /></el-icon>
                  点击生成任务拆解
                </el-button>
              </div>
            </el-tab-pane>
            <el-tab-pane label="AI对话" name="chat">
              <div class="ai-chat-container">
                <div class="ai-chat-messages" ref="aiChatMessagesRef">
                  <div v-for="(msg, index) in aiChatMessages" :key="index" :class="['ai-chat-message', msg.role]">
                    <div class="message-avatar">
                      <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                      <el-icon v-else><MagicStick /></el-icon>
                    </div>
                    <div class="message-content">{{ msg.content }}</div>
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
                    @keydown.enter="handleAiChat"
                    :disabled="aiChatLoading"
                  />
                  <el-button type="primary" :loading="aiChatLoading" @click="handleAiChat" :disabled="!aiChatInput.trim()">
                    发送
                  </el-button>
                </div>
              </div>
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

          <!-- 回复输入 -->
          <div class="comment-input glass-card-static">
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
          </div>
        </el-card>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ticketApi } from '@/api'
import { useTicketStore } from '@/stores'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ChatDotRound, User, Star, MagicStick, Check, Download } from '@element-plus/icons-vue'
import type { TicketDTO, CommentDTO } from '@/types'

const router = useRouter()
const route = useRoute()
const ticketStore = useTicketStore()

const ticket = ref<TicketDTO>()
const comments = ref<CommentDTO[]>([])
const newComment = ref('')
const isInternal = ref(false)
const loading = ref(false)
const submitting = ref(false)
const analyzing = ref(false)
const activeAiTab = ref('tech')

// AI聊天相关
const aiChatMessages = ref<Array<{role: string, content: string}>>([])
const aiChatInput = ref('')
const aiChatLoading = ref(false)
const aiChatMessagesRef = ref<HTMLElement>()

async function loadTicket() {
  loading.value = true
  try {
    const id = Number(route.params.id)
    ticket.value = await ticketApi.getTicket(id)
    comments.value = ticket.value.comments || []
  } finally {
    loading.value = false
  }
}

async function handlePickup() {
  if (!ticket.value) return
  try {
    await ElMessageBox.confirm('确定要接这个项目吗？接单后将与发布者开始协作。', '确认接单')
    ticket.value = await ticketApi.pickupTicket(ticket.value.id)
    ticketStore.triggerRefresh()
    ElMessage.success('接单成功！请进入聊天与发布者沟通')
  } catch {
    // cancelled
  }
}

async function handleAiAnalysis() {
  if (!ticket.value) return
  analyzing.value = true
  try {
    ticket.value = await ticketApi.analyzeTicket(ticket.value.id)
    ElMessage.success('AI分析完成')
  } catch {
    ElMessage.error('AI分析失败，请稍后重试')
  } finally {
    analyzing.value = false
  }
}

async function handleDownloadPrd() {
  if (!ticket.value) return
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
    ElMessage.success('PRD下载成功')
  } catch {
    ElMessage.error('下载失败，请稍后重试')
  }
}

async function handleAiChat() {
  if (!ticket.value || !aiChatInput.value.trim() || aiChatLoading.value) return

  const userMessage = aiChatInput.value.trim()
  aiChatMessages.value.push({ role: 'user', content: userMessage })
  aiChatInput.value = ''
  aiChatLoading.value = true

  try {
    const response = await ticketApi.aiChat(ticket.value.id, userMessage)
    aiChatMessages.value.push({ role: 'assistant', content: response.content })
    await nextTick()
    if (aiChatMessagesRef.value) {
      aiChatMessagesRef.value.scrollTop = aiChatMessagesRef.value.scrollHeight
    }
  } catch {
    ElMessage.error('AI回复失败，请稍后重试')
  } finally {
    aiChatLoading.value = false
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

function getCategoryType(category: string) {
  const map: Record<string, string> = {
    BUG: 'danger',
    CONSULT: 'primary',
    COMPLAINT: 'warning',
    SUGGESTION: 'success',
    OTHER: 'info'
  }
  return map[category] || 'info'
}

function getCategoryLabel(category: string) {
  const map: Record<string, string> = {
    BUG: 'Bug',
    CONSULT: '咨询',
    COMPLAINT: '投诉',
    SUGGESTION: '建议',
    OTHER: '其他'
  }
  return map[category] || category
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

function getStatusType(status: string) {
  const map: Record<string, string> = {
    OPEN: 'warning',
    ACCEPTED: 'primary',
    PENDING_APPROVAL: 'warning',
    COMPLETED: 'success',
    REJECTED: 'danger',
    CLOSED: 'info'
  }
  return map[status] || 'info'
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    OPEN: '待接单',
    ACCEPTED: '开发中',
    PENDING_APPROVAL: '待验收',
    COMPLETED: '已完成',
    REJECTED: '已拒绝',
    CLOSED: '已关闭'
  }
  return map[status] || status
}

onMounted(() => {
  loadTicket()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

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

.btn-glass-success {
  background: linear-gradient(135deg, var(--accent-success), #7dffb3) !important;
}
</style>
