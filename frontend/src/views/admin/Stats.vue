<template>
  <div class="stats-page">
    <div class="page-header">
      <h2>统计分析</h2>
    </div>

    <div class="stats-grid">
      <el-card class="stat-card glass-card" shadow="hover">
        <div class="stat-icon stat-icon-total">
          <el-icon size="28"><Ticket /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ total }}</div>
          <div class="stat-label">工单总数</div>
        </div>
      </el-card>

      <el-card class="stat-card glass-card" shadow="hover">
        <div class="stat-icon stat-icon-pending">
          <el-icon size="28"><Clock /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.open }}</div>
          <div class="stat-label">待处理</div>
        </div>
      </el-card>

      <el-card class="stat-card glass-card" shadow="hover">
        <div class="stat-icon stat-icon-processing">
          <el-icon size="28"><Loading /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.processing }}</div>
          <div class="stat-label">处理中</div>
        </div>
      </el-card>

      <el-card class="stat-card glass-card" shadow="hover">
        <div class="stat-icon stat-icon-resolved">
          <el-icon size="28"><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.resolved }}</div>
          <div class="stat-label">已解决</div>
        </div>
      </el-card>

      <el-card class="stat-card glass-card" shadow="hover">
        <div class="stat-icon stat-icon-closed">
          <el-icon size="28"><Lock /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.closed }}</div>
          <div class="stat-label">已关闭</div>
        </div>
      </el-card>
    </div>

    <el-card class="chart-card glass-card">
      <template #header>
        <span class="card-title">工单状态分布</span>
      </template>
      <div class="chart-placeholder">
        <el-progress
          type="dashboard"
          :percentage="totalPercentage"
          :color="progressColors"
        >
          <template #default>
            <span class="total-label">总数</span>
            <span class="total-value">{{ total }}</span>
          </template>
        </el-progress>
        <div class="legend">
          <div class="legend-item">
            <span class="dot stat-icon-pending"></span>
            <span>待处理 ({{ stats.open }})</span>
          </div>
          <div class="legend-item">
            <span class="dot stat-icon-processing"></span>
            <span>处理中 ({{ stats.processing }})</span>
          </div>
          <div class="legend-item">
            <span class="dot stat-icon-resolved"></span>
            <span>已解决 ({{ stats.resolved }})</span>
          </div>
          <div class="legend-item">
            <span class="dot stat-icon-closed"></span>
            <span>已关闭 ({{ stats.closed }})</span>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ticketApi } from '@/api'
import { Ticket, Clock, Loading, CircleCheck, Lock } from '@element-plus/icons-vue'
import type { TicketStatsDTO } from '@/types'

const stats = ref<TicketStatsDTO>({ open: 0, processing: 0, resolved: 0, closed: 0 })

const total = computed(() => stats.value.open + stats.value.processing + stats.value.resolved + stats.value.closed)
const totalPercentage = computed(() => (total.value > 0 ? 100 : 0))

const progressColors = [
  { color: 'var(--accent-warning)', percentage: 25 },
  { color: 'var(--accent-primary)', percentage: 50 },
  { color: 'var(--accent-success)', percentage: 75 },
  { color: 'var(--text-tertiary)', percentage: 100 }
]

async function loadStats() {
  try {
    stats.value = await ticketApi.getStats()
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadStats()
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

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-icon-total {
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
  box-shadow: 0 4px 20px rgba(0, 122, 255, 0.4);
}

.stat-icon-pending {
  background: linear-gradient(135deg, var(--accent-warning), #ffb347);
  box-shadow: 0 4px 20px rgba(255, 159, 10, 0.4);
}

.stat-icon-processing {
  background: linear-gradient(135deg, var(--accent-info), var(--accent-primary));
  box-shadow: 0 4px 20px rgba(100, 210, 255, 0.4);
}

.stat-icon-resolved {
  background: linear-gradient(135deg, var(--accent-success), #7dffb3);
  box-shadow: 0 4px 20px rgba(48, 209, 88, 0.4);
}

.stat-icon-closed {
  background: linear-gradient(135deg, var(--text-tertiary), var(--text-secondary));
  box-shadow: 0 4px 20px rgba(255, 255, 255, 0.1);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.chart-card {
  margin-bottom: 20px;
}

.card-title {
  font-weight: 600;
  color: var(--text-primary);
  font-size: 16px;
}

.chart-placeholder {
  display: flex;
  align-items: center;
  gap: 80px;
  padding: 24px;
}

.total-label {
  display: block;
  font-size: 14px;
  color: var(--text-tertiary);
}

.total-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.legend {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--text-secondary);
}

.dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
</style>
