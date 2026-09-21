<template>
  <div class="project-list">
    <!-- 统计卡片 -->
    <div class="stats-cards">
      <el-card v-for="stat in stats" :key="stat.status" class="stat-card" shadow="hover"
        :class="{ active: currentStatus === stat.status }" @click="filterByStatus(stat.status)">
        <div class="stat-value">{{ stat.count }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </el-card>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button type="primary" @click="$router.push('/projects/create')">
        <el-icon><Plus /></el-icon>
        创建项目
      </el-button>

      <div class="filter-group">
        <el-select v-model="currentStatus" placeholder="项目状态" clearable @change="loadProjects">
          <el-option label="规划中" value="PLANNING" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="测试中" value="TESTING" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
      </div>
    </div>

    <!-- 项目列表 -->
    <div class="project-grid">
      <el-card v-for="project in projects" :key="project.id" class="project-card" shadow="hover"
        @click="$router.push(`/projects/${project.id}`)">
        <div class="project-header">
          <span class="project-type-tag" :type="getProjectType(project.projectType)">
            {{ getProjectTypeLabel(project.projectType) }}
          </span>
          <el-tag :type="getStatusType(project.status)" size="small">
            {{ getStatusLabel(project.status) }}
          </el-tag>
        </div>

        <h3 class="project-name">{{ project.name }}</h3>
        <p class="project-desc">{{ project.description }}</p>

        <div class="project-progress">
          <div class="progress-info">
            <span>进度</span>
            <span>{{ project.progress }}%</span>
          </div>
          <el-progress :percentage="project.progress" :status="project.progress === 100 ? 'success' : ''" />
        </div>

        <div class="project-meta">
          <span><el-icon><Clock /></el-icon> {{ formatDate(project.createdAt) }}</span>
          <span v-if="project.deadline">
            <el-icon><Calendar /></el-icon> 截止: {{ formatDate(project.deadline) }}
          </span>
        </div>

        <div class="project-stats">
          <span>任务: {{ project.completedTasks }}/{{ project.totalTasks }}</span>
          <span v-if="project.aiEstimatedHours">预估: {{ project.aiEstimatedHours }}h</span>
        </div>
      </el-card>

      <el-empty v-if="projects.length === 0" description="暂无项目" />
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadProjects"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, Clock, Calendar } from '@element-plus/icons-vue'
import { projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const projects = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const currentStatus = ref('')

const stats = ref([
  { status: 'PLANNING', label: '规划中', count: 0 },
  { status: 'IN_PROGRESS', label: '进行中', count: 0 },
  { status: 'COMPLETED', label: '已完成', count: 0 },
])

const loadProjects = async () => {
  try {
    let res
    if (currentStatus.value) {
      res = await projectApi.getByStatus(currentStatus.value, currentPage.value - 1, pageSize.value)
    } else {
      // 默认加载我的项目。这里原本硬编码 userId=1，等于所有登录用户都只能看到
      // 1 号用户的项目；路由接上之后这个错误会真实地暴露给每个用户。
      const userId = authStore.userInfo?.id
      if (!userId) {
        // 登录态不完整（例如 localStorage 被清过一半），不带着 undefined 去打接口
        projects.value = []
        total.value = 0
        return
      }
      res = await projectApi.getMyProjects(userId, currentPage.value - 1, pageSize.value)
    }
    projects.value = res.data.content || []
    total.value = res.data.totalElements || 0
  } catch (error) {
    console.error('加载项目列表失败', error)
  }
}

const loadStats = async () => {
  try {
    for (const stat of stats.value) {
      const res = await projectApi.countByStatus(stat.status)
      stat.count = res.data || 0
    }
  } catch (error) {
    console.error('加载统计失败', error)
  }
}

const filterByStatus = (status) => {
  currentStatus.value = currentStatus.value === status ? '' : status
  loadProjects()
}

const getProjectTypeLabel = (type) => {
  const map = { THESIS: '毕设', OUTSOURCE: '外包', INTERNAL: '内部', PERSONAL: '个人' }
  return map[type] || type
}

const getProjectType = (type) => {
  const map = { THESIS: 'primary', OUTSOURCE: 'warning', INTERNAL: 'info', PERSONAL: 'success' }
  return map[type] || ''
}

const getStatusType = (status) => {
  const map = { PLANNING: 'info', IN_PROGRESS: '', TESTING: 'warning', COMPLETED: 'success', ARCHIVED: 'info' }
  return map[status] || ''
}

const getStatusLabel = (status) => {
  const map = { PLANNING: '规划中', IN_PROGRESS: '进行中', TESTING: '测试中', COMPLETED: '已完成', ARCHIVED: '已归档' }
  return map[status] || status
}

const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadProjects()
  loadStats()
})
</script>

<style scoped>
.project-list {
  padding: 20px;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.3s;
  text-align: center;
}

.stat-card.active {
  border-color: #409eff;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  color: #909399;
  margin-top: 4px;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.filter-group {
  display: flex;
  gap: 12px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.project-card {
  cursor: pointer;
  transition: all 0.3s;
  border-radius: 8px;
}

.project-card:hover {
  transform: translateY(-4px);
}

.project-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.project-type-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #ecf5ff;
  color: #409eff;
}

.project-name {
  font-size: 16px;
  margin: 0 0 8px 0;
  color: #303133;
}

.project-desc {
  color: #606266;
  font-size: 13px;
  margin: 0 0 16px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-progress {
  margin-bottom: 12px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.project-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.project-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.project-stats {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
