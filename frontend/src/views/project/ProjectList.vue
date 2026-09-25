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
        <!-- 这里原本有个「测试中 / TESTING」选项，永远不会筛出结果（没有任何代码
             把项目置为该状态），已删。后端 ProjectRepository 的查询里仍含 TESTING，
             若将来它变得可达，记得同时补回这个选项和上面的统计卡。 -->
        <el-select v-model="currentStatus" placeholder="项目状态" clearable @change="loadProjects">
          <el-option label="规划中" value="PLANNING" />
          <el-option label="进行中" value="IN_PROGRESS" />
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
          <span class="project-type-tag">
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

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Clock, Calendar } from '@element-plus/icons-vue'
import { projectApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { ProjectDTO, ProjectStatus, ProjectType } from '@/types'

/** el-tag / el-progress 的 type 取值，写成字面量联合才能当合法属性传进去。 */
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const authStore = useAuthStore()

const projects = ref<ProjectDTO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
// '' 表示「不限状态」，所以类型不能只有 ProjectStatus。
const currentStatus = ref<ProjectStatus | ''>('')

// 统计卡的 status 必须是 ProjectStatus。此前它是个裸数组，'TESTING' 这种后端
// 从不产生的值也能写进来 —— 结果是一张永远是 0、点了还没结果的卡片。
const stats = ref<Array<{ status: ProjectStatus; label: string; count: number }>>([
  { status: 'PLANNING', label: '规划中', count: 0 },
  { status: 'IN_PROGRESS', label: '进行中', count: 0 },
  { status: 'COMPLETED', label: '已完成', count: 0 }
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

const filterByStatus = (status: ProjectStatus) => {
  currentStatus.value = currentStatus.value === status ? '' : status
  loadProjects()
}

const projectTypeLabels: Record<ProjectType, string> = {
  THESIS: '毕设',
  OUTSOURCE: '外包',
  INTERNAL: '内部',
  PERSONAL: '个人'
}

// 查表失败时回退显示原始值：后端若新增了状态，界面显示原始字符串比显示空白更容易定位。
const getProjectTypeLabel = (type?: ProjectType) => (type ? projectTypeLabels[type] || type : '-')

// 模板里 <span class="project-type-tag"> 曾经绑了个 :type="getProjectType(...)"，
// 但 span 没有 type 属性、配色全部来自 CSS 类，这个函数算了却没人用 —— 已删。

const statusTagTypes: Record<ProjectStatus, TagType | ''> = {
  PLANNING: 'info',
  IN_PROGRESS: '',
  COMPLETED: 'success',
  ARCHIVED: 'info'
}

const statusLabels: Record<ProjectStatus, string> = {
  PLANNING: '规划中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  ARCHIVED: '已归档'
}

const getStatusType = (status?: ProjectStatus) => (status ? statusTagTypes[status] : '')
const getStatusLabel = (status?: ProjectStatus) => (status ? statusLabels[status] || status : '-')

const formatDate = (date?: string) => {
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
  border-color: var(--accent-primary);
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-label {
  color: var(--text-secondary);
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
  background: rgba(0, 122, 255, 0.25);
  color: var(--text-primary);
}

.project-name {
  font-size: 16px;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.project-desc {
  color: var(--text-secondary);
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
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.project-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);
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
  color: var(--text-secondary);
  padding-top: 12px;
  border-top: 1px solid var(--glass-border);
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
