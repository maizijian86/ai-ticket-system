<template>
  <div class="pending-tickets">
    <div class="page-header">
      <h2>待处理工单</h2>
      <p class="page-desc">你已接单、尚未完成验收的工单。开发完成后在这里提交交付凭证。</p>
    </div>

    <el-card class="table-card glass-card-static">
      <el-table :data="tickets" v-loading="loading" class="glass-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link :to="`/tickets/${row.id}`" class="ticket-title">{{ row.title }}</router-link>
            <!-- 驳回理由必须让接单者看见，否则他只知道"被拒了"却不知道改什么 -->
            <div v-if="row.status === 'REJECTED' && row.rejectionReason" class="reject-reason">
              驳回理由：{{ row.rejectionReason }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.category" :type="getCategoryType(row.category)" size="small" class="glass-tag">
              {{ getCategoryLabel(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80">
          <template #default="{ row }">
            <span :class="`priority-${row.priority}`" v-if="row.priority">{{ row.priority }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" class="glass-tag">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="发布者" width="100" />
        <el-table-column prop="acceptedAt" label="接单时间" width="160">
          <template #default="{ row }">
            {{ row.acceptedAt ? formatDate(row.acceptedAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <!--
              这里以前是一个「领取」按钮，调 assignHandler。那条路径只写 handler_id，
              不改状态也不改 is_visible —— 点完列表刷新出来一模一样，看着像没反应。
              接单已移到大厅，这个菜单只剩"把已接的单推进下去"。
            -->
            <el-button
              v-if="row.status === 'ACCEPTED' || row.status === 'REJECTED'"
              type="primary"
              size="small"
              @click="openCompleteDialog(row)"
              class="btn-glass btn-glass-primary"
            >
              去完成
            </el-button>
            <el-tooltip v-else content="已提交交付，等待发布者验收" placement="top">
              <el-button size="small" disabled class="btn-glass">待验收</el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadTickets"
        @size-change="loadTickets"
        class="pagination glass-pagination"
      />
    </el-card>

    <!-- 去完成：填交付凭证。弹窗与工单详情页共用同一个组件 -->
    <CompleteDeliveryDialog v-model="completeVisible" :ticket="current" @done="loadTickets" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ticketApi } from '@/api'
import { useAuthStore } from '@/stores'
import CompleteDeliveryDialog from '@/components/CompleteDeliveryDialog.vue'
import type { TicketDTO } from '@/types'
import { getStatusLabel, getStatusType, getCategoryLabel, getCategoryType } from '@/utils/ticket-labels'

const authStore = useAuthStore()
const tickets = ref<TicketDTO[]>([])
const loading = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

async function loadTickets() {
  const me = authStore.userInfo?.id
  if (!me) return
  loading.value = true
  try {
    const res = await ticketApi.listTickets({
      handlerId: me,
      // 三个参数缺一不可：
      //   statuses   —— 逗号拼接字符串（后端文档化并验证过的形式）
      //   includeAll —— 跳过"只查大厅可见"谓词，否则接单后 is_visible=false，一条都查不到
      //   handlerId  —— includeAll 只管可见性，归属还得自己说
      // 加上 REJECTED：被驳回的工单要回到这里，接单者才知道需要返工。
      statuses: 'ACCEPTED,PENDING_APPROVAL,REJECTED',
      includeAll: true,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    tickets.value = res.data
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

// ---------------- 去完成 ----------------

const completeVisible = ref(false)
const current = ref<TicketDTO | null>(null)

function openCompleteDialog(ticket: TicketDTO) {
  current.value = ticket
  completeVisible.value = true
}

function formatDate(date: string) {
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  loadTickets()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.page-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.table-card {
  margin-bottom: 20px;
}

.ticket-title {
  color: var(--accent-info);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s ease;
}

.ticket-title:hover {
  color: var(--accent-primary);
  text-decoration: underline;
}

.reject-reason {
  margin-top: 4px;
  font-size: 12px;
  color: var(--accent-danger, #f56c6c);
  line-height: 1.4;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
