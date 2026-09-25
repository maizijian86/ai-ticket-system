<template>
  <div class="completed-tickets">
    <div class="page-header">
      <h2>已完成</h2>
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
        <el-table-column prop="creatorName" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="resolvedAt" label="完成时间" width="160">
          <template #default="{ row }">
            {{ row.resolvedAt ? formatDate(row.resolvedAt) : '-' }}
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ticketApi } from '@/api'
import { useAuthStore } from '@/stores'
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
    // 和 AcceptedTickets 同一个坑：靠 listTickets 的自动收窄 + status 是走不通的，
    // 那个查询带着「只查大厅可见」谓词，而接单会把 is_visible 置为 false。
    //
    // 这里必须显式写全三个条件：归属（handlerId）、可见性豁免（includeAll）、
    // 状态（status）。includeAll 只管可见性，不管归属 —— 只传它会把"我完成的"
    // 放大成"全站已完成的"。
    const res = await ticketApi.listTickets({
      status: 'COMPLETED',
      handlerId: me,
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
</style>
