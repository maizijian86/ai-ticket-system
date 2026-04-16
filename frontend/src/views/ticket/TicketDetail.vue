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
              <span class="label">创建人</span>
              <span>{{ ticket.creatorName }}</span>
            </div>
            <div class="meta-item">
              <span class="label">处理人</span>
              <span>{{ ticket.handlerName || '待分配' }}</span>
            </div>
            <div class="meta-item">
              <span class="label">创建时间</span>
              <span>{{ formatDate(ticket.createdAt) }}</span>
            </div>
            <div class="meta-item" v-if="ticket.resolvedAt">
              <span class="label">解决时间</span>
              <span>{{ formatDate(ticket.resolvedAt) }}</span>
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
            <el-button @click="router.push(`/tickets/${ticket.id}/edit`)" class="btn-glass">
              编辑
            </el-button>
            <el-button v-if="ticket.status === 'OPEN'" type="primary" @click="handleStartProcessing" class="btn-glass btn-glass-primary">
              开始处理
            </el-button>
            <el-button v-if="ticket.status === 'PROCESSING'" type="success" @click="handleResolve" class="btn-glass btn-glass-success">
              标记已解决
            </el-button>
            <el-button v-if="ticket.status === 'RESOLVED'" @click="handleReopen" class="btn-glass">
              重新打开
            </el-button>
            <el-button v-if="ticket.status !== 'CLOSED'" @click="handleClose" class="btn-glass">
              关闭工单
            </el-button>
          </div>
        </el-card>

        <!-- 工单内容 -->
        <el-card class="ticket-content-card glass-card">
          <div class="ticket-content">
            <h3>问题描述</h3>
            <div class="content-text">{{ ticket.content }}</div>
          </div>
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
                <div v-if="comment.references?.length" class="comment-references glass-card-static">
                  <div class="references-title">参考来源</div>
                  <div v-for="ref in comment.references" :key="ref.source" class="reference-item">
                    {{ ref.source }} (相似度 {{ (ref.similarity * 100).toFixed(0) }}%)
                  </div>
                </div>
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
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ticketApi } from '@/api'
import { useTicketStore } from '@/stores'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ChatDotRound, User, Star } from '@element-plus/icons-vue'
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

async function handleStartProcessing() {
  if (!ticket.value) return
  try {
    ticket.value = await ticketApi.startProcessing(ticket.value.id)
    ticketStore.triggerRefresh()
    ElMessage.success('已开始处理')
  } catch {
    // error handled
  }
}

async function handleResolve() {
  if (!ticket.value) return
  try {
    ticket.value = await ticketApi.resolve(ticket.value.id)
    ticketStore.triggerRefresh()
    ElMessage.success('已标记为解决')
  } catch {
    // error handled
  }
}

async function handleReopen() {
  if (!ticket.value) return
  try {
    ticket.value = await ticketApi.reopen(ticket.value.id)
    ticketStore.triggerRefresh()
    ElMessage.success('工单已重新打开')
  } catch {
    // error handled
  }
}

async function handleClose() {
  if (!ticket.value) return
  try {
    await ElMessageBox.confirm('确定要关闭此工单吗？', '确认关闭')
    ticket.value = await ticketApi.close(ticket.value.id)
    ticketStore.triggerRefresh()
    ElMessage.success('工单已关闭')
  } catch {
    // cancelled
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
    open: 'warning',
    processing: 'primary',
    resolved: 'success',
    closed: 'info'
  }
  return map[status] || 'info'
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    open: '待处理',
    processing: '处理中',
    resolved: '已解决',
    closed: '已关闭'
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

.comment-references {
  margin-top: 12px;
  padding: 12px;
  border-radius: var(--radius-md) !important;
}

.references-title {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.reference-item {
  font-size: 13px;
  color: var(--accent-info);
  margin-top: 6px;
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
