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
import { getRoleLabel, getRoleType } from '@/utils/role-labels'
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

// 此处原有两个局部 map（getRoleType / getRoleLabel）。它们被提到
// utils/role-labels.ts：个人主页的「账号信息」也要显示身份，抄一份必然漂移。
// 这两个 map 曾经只有 ADMIN / USER —— 后端加了 HANDLER 之后没跟上，于是那些行在
// 页面上显示成英文原文 "HANDLER" 配一个灰标签。

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
