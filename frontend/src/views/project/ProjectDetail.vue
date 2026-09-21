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

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi } from '@/api'
import TaskBoard from './components/TaskBoard.vue'
import ModuleList from './components/ModuleList.vue'
import MilestoneList from './components/MilestoneList.vue'
import AiAnalysis from './components/AiAnalysis.vue'
import DiscussionList from './components/DiscussionList.vue'

const route = useRoute()
const router = useRouter()
const project = ref(null)
const activeTab = ref('tasks')

const loadProject = async () => {
  try {
    const res = await projectApi.getById(route.params.id)
    // 后端「项目不存在」是 HTTP 200 + code!=200，不会走 catch。
    // 不显式判断的话 project 会停在 undefined，而模板整段包在 v-if="project" 里，
    // 结果是白屏而不是报错——所以在这里主动抛出去，交给下面的 catch 处理。
    if (!res.data) {
      throw new Error(res.message || '项目不存在')
    }
    project.value = res.data
  } catch (error) {
    ElMessage.error(error.message || '加载项目失败')
    router.push('/projects')
  }
}

const startProject = async () => {
  try {
    await projectApi.start(project.value.id)
    ElMessage.success('项目已开始')
    loadProject()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const completeProject = async () => {
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

const getProjectTypeLabel = (type) => {
  const map = { THESIS: '毕设', OUTSOURCE: '外包', INTERNAL: '内部', PERSONAL: '个人' }
  return map[type] || type
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
  color: #303133;
}

.header-meta {
  display: flex;
  gap: 12px;
  align-items: center;
  color: #909399;
  font-size: 13px;
}

.project-type {
  padding: 2px 8px;
  background: #f5f7fa;
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
  color: #303133;
}

.progress-label {
  color: #909399;
  font-size: 13px;
}
</style>
