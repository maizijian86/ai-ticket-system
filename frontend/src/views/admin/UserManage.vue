<template>
  <div class="user-manage">
    <div class="page-header">
      <h2>用户管理</h2>
    </div>

    <el-card class="table-card glass-card-static">
      <el-table :data="users" v-loading="loading" class="glass-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.role)" size="small" class="glass-tag">
              {{ getRoleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" class="glass-tag">
              {{ row.status === 'active' ? '活跃' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadUsers"
        @size-change="loadUsers"
        class="pagination glass-pagination"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { userApi } from '@/api'
import type { UserDTO } from '@/types'

const users = ref<UserDTO[]>([])
const loading = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

async function loadUsers() {
  loading.value = true
  try {
    const res = await userApi.listUsers(pagination.page, pagination.pageSize)
    users.value = res.data
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

function formatDate(date?: string) {
  return date ? new Date(date).toLocaleString('zh-CN') : '-'
}

/*
 * 两个 map 都曾经只有 ADMIN / USER —— 后端加了 HANDLER 之后没跟上，于是那些行在
 * 页面上显示成英文原文 "HANDLER" 配一个灰标签。四个值现在都在这里，别再漏。
 *
 * 保留 `|| 'info'` / `|| role` 的兜底：库里 role 是 VARCHAR(20) 不是 MySQL ENUM，
 * 出现枚举外的值时应该显示原文而不是渲染成空白。
 */
function getRoleType(role: string) {
  const map: Record<string, string> = {
    ADMIN: 'danger',
    PUBLISHER: 'success',
    HANDLER: 'warning',
    USER: 'primary'
  }
  return map[role] || 'info'
}

function getRoleLabel(role: string) {
  const map: Record<string, string> = {
    ADMIN: '管理员',
    PUBLISHER: '发布者',
    HANDLER: '接单者',
    // 存量身份：发布与接单都能做。标签只写"用户"是因为这一列宽 100px，
    // 带上括号说明会换行 —— 含义见 backend common/enums/Role.java 的 javadoc。
    USER: '用户'
  }
  return map[role] || role
}

onMounted(() => {
  loadUsers()
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

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
