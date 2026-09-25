<template>
  <div class="project-detail" v-if="project">
    <!-- 项目头部 -->
    <div class="project-header">
      <div class="header-left">
        <el-button text @click="$router.push('/projects')">← 返回列表</el-button>
        <h1>{{ project.name }}</h1>
        <div class="header-meta">
          <el-tag :type="getStatusType(project.status)">{{ getStatusLabel(project.status) }}</el-tag>
          <span class="project-type">{{ getProjectTypeLabel(project.projectType) }}</span>
          <span v-if="project.deadline">截止: {{ formatDate(project.deadline) }}</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button v-if="project.status === 'PLANNING'" type="primary" @click="startProject">
          开始项目
        </el-button>
        <el-button v-if="project.status === 'IN_PROGRESS'" type="success" @click="completeProject">
          完成项目
        </el-button>
      </div>
    </div>

    <!-- 进度概览 -->
    <el-card class="progress-card">
      <div class="progress-stats">
        <div class="progress-item">
          <div class="progress-value">{{ project.progress }}%</div>
          <div class="progress-label">总进度</div>
        </div>
        <div class="progress-item">
          <div class="progress-value">{{ project.completedTasks }}/{{ project.totalTasks }}</div>
          <div class="progress-label">任务完成</div>
        </div>
        <div class="progress-item">
          <div class="progress-value">{{ project.aiEstimatedDays || '-' }}天</div>
          <div class="progress-label">AI预估</div>
        </div>
      </div>
      <el-progress :percentage="project.progress" :stroke-width="20" />
    </el-card>

    <!-- Tab导航 -->
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 任务看板 -->
      <el-tab-pane label="任务看板" name="tasks">
        <TaskBoard :projectId="project.id" @refresh="loadProject" />
      </el-tab-pane>

      <!-- 模块管理 -->
      <el-tab-pane label="模块管理" name="modules">
        <ModuleList :projectId="project.id" @refresh="loadProject" />
      </el-tab-pane>

      <!-- 里程碑 -->
      <el-tab-pane label="里程碑" name="milestones">
        <MilestoneList :projectId="project.id" />
      </el-tab-pane>

      <!-- AI分析 -->
      <el-tab-pane label="AI分析" name="ai">
        <AiAnalysis :project="project" @refresh="loadProject" />
      </el-tab-pane>

      <!-- 讨论区 -->
      <el-tab-pane label="讨论" name="discussion">
        <DiscussionList :projectId="project.id" :targetType="'PROJECT'" :targetId="project.id" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi } from '@/api'
import type { ProjectDTO, ProjectStatus, ProjectType } from '@/types'
import TaskBoard from './components/TaskBoard.vue'
import ModuleList from './components/ModuleList.vue'
import MilestoneList from './components/MilestoneList.vue'
import AiAnalysis from './components/AiAnalysis.vue'
import DiscussionList from './components/DiscussionList.vue'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const route = useRoute()
const router = useRouter()
const project = ref<ProjectDTO | null>(null)
const activeTab = ref('tasks')

const backToList = () => {
  router.push('/projects')
}

const loadProject = async () => {
  // route.params.id 是 string | string[]，而接口要的是数字。
  // 不转换的话 '/projects/abc' 会带着字符串去打后端，只能靠后端报错兜回来 ——
  // 与其指望后端，不如在这里判断掉：Number('abc') 得到 NaN，被 isFinite 挡下。
  // 注意别指望它挡住数组：Number(['5']) === 5，单元素数组是能过这个判断的
  // （本路由是 projects/:id 而非 :id+，params.id 不会是数组，所以够用）。
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    ElMessage.error('项目 ID 非法')
    backToList()
    return
  }

  try {
    const res = await projectApi.getById(id)
    // 后端「项目不存在」是 HTTP 200 + code!=200，不会走 catch。
    // 不显式判断的话 project 会停在 undefined，而模板整段包在 v-if="project" 里，
    // 结果是白屏而不是报错——所以在这里主动抛出去，交给下面的 catch 处理。
    if (!res.data) {
      throw new Error(res.message || '项目不存在')
    }
    project.value = res.data
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载项目失败')
    backToList()
  }
}

const startProject = async () => {
  if (!project.value) return
  try {
    await projectApi.start(project.value.id)
    ElMessage.success('项目已开始')
    loadProject()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const completeProject = async () => {
  if (!project.value) return
  try {
    await ElMessageBox.confirm('确定要标记项目为完成吗？', '确认')
    await projectApi.complete(project.value.id)
    ElMessage.success('项目已完成')
    loadProject()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const projectTypeLabels: Record<ProjectType, string> = {
  THESIS: '毕设',
  OUTSOURCE: '外包',
  INTERNAL: '内部',
  PERSONAL: '个人'
}

// 这里原本还有 TESTING，已删。注意这**不是**纯类型问题，而是一个判断：
// 没有任何代码会把项目置为 TESTING，所以它当下不可达 —— 但后端并非不认识它，
// Project.java 的状态注释里列了它，ProjectRepository.findActiveProjects() 的
// IN 列表也含它。将来若真有人开始写 TESTING，下面两个查表会失败并回退成
// 原始字符串（界面还能看，不至于空白），只是筛选下拉框和统计卡里不会有这一项。
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

// 查表失败时回退显示原始值：后端若新增了状态，界面显示原始字符串比显示空白更容易定位。
const getProjectTypeLabel = (type?: ProjectType) => (type ? projectTypeLabels[type] || type : '-')
const getStatusType = (status?: ProjectStatus) => (status ? statusTagTypes[status] : '')
const getStatusLabel = (status?: ProjectStatus) => (status ? statusLabels[status] || status : '-')

const formatDate = (date?: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadProject()
})
</script>

<style scoped>
.project-detail {
  padding: 20px;
}

.project-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.header-left h1 {
  margin: 8px 0;
  color: var(--text-primary);
}

.header-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.project-type {
  padding: 2px 8px;
  background: var(--glass-bg-hover);
  border-radius: 4px;
}

.progress-card {
  margin-bottom: 20px;
}

.progress-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 20px;
  text-align: center;
}

.progress-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
}

.progress-label {
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
