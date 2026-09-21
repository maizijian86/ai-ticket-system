<template>
  <div class="not-found">
    <div class="not-found-card glass-card">
      <div class="code">404</div>
      <h2 class="title">页面不存在</h2>
      <p class="desc">
        没有找到 <code>{{ route.fullPath }}</code>
      </p>
      <div class="actions">
        <el-button type="primary" class="btn-glass btn-glass-primary" @click="router.back()">
          返回上一页
        </el-button>
        <el-button class="btn-glass" @click="router.push('/tickets')">
          回需求大厅
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.not-found-card {
  padding: 48px 56px;
  text-align: center;
  border-radius: var(--radius-xl) !important;
  max-width: 480px;
}

.code {
  font-size: 72px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -2px;
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-tertiary));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.title {
  margin: 16px 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.desc {
  margin: 0 0 28px;
  font-size: 14px;
  color: var(--text-tertiary);
  word-break: break-all;
}

.desc code {
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  background: var(--glass-bg-hover);
  color: var(--text-secondary);
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
