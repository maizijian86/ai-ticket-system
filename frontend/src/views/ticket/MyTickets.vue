<template>
  <div class="my-tickets">
    <div class="page-header">
      <h2>我的工单</h2>
    </div>

    <el-card class="table-card glass-card-static">
      <el-table :data="tickets" v-loading="loading" class="glass-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <router-link :to="`/tickets/${row.id}`" class="ticket-title">{{ row.title }}</router-link>
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
        <el-table-column prop="handlerName" label="处理人" width="100">
          <template #default="{ row }">
            <span>{{ row.handlerName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <!--
              验收入口开在发布者这一侧。此前整条交付/验收链路后端都有、前端零调用，
              于是接单者交了货也没人能验收 —— 工单永远停在"待验收"。
            -->
            <el-button
              v-if="row.status === 'PENDING_APPROVAL'"
              type="primary"
              size="small"
              @click="openReviewDialog(row)"
              class="btn-glass btn-glass-primary"
            >
              去验收
            </el-button>
            <span v-else class="no-action">-</span>
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

    <!-- 验收：通过 / 驳回。弹窗与工单详情页共用同一个组件 -->
    <ReviewDeliveryDialog v-model="reviewVisible" :ticket="current" @done="loadTickets" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ticketApi } from '@/api'
import ReviewDeliveryDialog from '@/components/ReviewDeliveryDialog.vue'
import type { TicketDTO } from '@/types'
import { getStatusLabel, getStatusType, getCategoryLabel, getCategoryType } from '@/utils/ticket-labels'

const tickets = ref<TicketDTO[]>([])
const loading = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

async function loadTickets() {
  loading.value = true
  try {
    const res = await ticketApi.listMyTickets(pagination.page, pagination.pageSize)
    tickets.value = res.data
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

// ---------------- 验收 ----------------

const reviewVisible = ref(false)
const current = ref<TicketDTO | null>(null)

function openReviewDialog(ticket: TicketDTO) {
  current.value = ticket
  reviewVisible.value = true
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

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.no-action {
  color: var(--text-secondary);
}
</style>
