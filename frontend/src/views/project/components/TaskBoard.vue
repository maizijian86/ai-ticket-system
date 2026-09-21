<template>
  <div class="task-board">
    <div class="board-header">
      <h3>任务看板</h3>
      <el-button type="primary" size="small" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        新建任务
      </el-button>
    </div>

    <div class="board-columns">
      <div v-for="column in columns" :key="column.status" class="board-column">
        <div class="column-header" :style="{ borderColor: column.color }">
          <span class="column-title">{{ column.title }}</span>
          <span class="column-count">{{ getColumnTasks(column.status).length }}</span>
        </div>
        <div class="column-body">
          <div v-for="task in getColumnTasks(column.status)" :key="task.id" class="task-card"
            @click="editTask(task)">
            <div class="task-header">
              <el-tag :type="getPriorityType(task.priority)" size="small">{{ task.priority }}</el-tag>
              <span class="task-type">{{ task.taskType }}</span>
            </div>
            <h4 class="task-title">{{ task.title }}</h4>
            <p class="task-desc">{{ task.description }}</p>
            <div class="task-footer">
              <span v-if="task.estimatedHours" class="task-hours">{{ task.estimatedHours }}h</span>
              <el-dropdown @command="(cmd) => moveTask(task, cmd)">
                <el-icon class="more-btn"><More /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="column.status !== 'TODO'" command="TODO">待办</el-dropdown-item>
                    <el-dropdown-item v-if="column.status !== 'IN_PROGRESS'" command="IN_PROGRESS">进行中</el-dropdown-item>
                    <el-dropdown-item v-if="column.status !== 'REVIEW'" command="REVIEW">评审</el-dropdown-item>
                    <el-dropdown-item v-if="column.status !== 'DONE'" command="DONE">完成</el-dropdown-item>
                    <el-dropdown-item divided command="delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <el-empty v-if="getColumnTasks(column.status).length === 0" :image-size="60" description="暂无任务" />
        </div>
      </div>
    </div>

    <!-- 创建/编辑任务对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingTask ? '编辑任务' : '新建任务'" width="500px">
      <el-form :model="taskForm" label-width="80px">
        <el-form-item label="任务标题">
          <el-input v-model="taskForm.title" placeholder="请输入任务标题" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="taskForm.description" type="textarea" :rows="3" placeholder="请输入任务描述" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="taskForm.taskType" placeholder="请选择">
            <el-option label="功能开发" value="FEATURE" />
            <el-option label="Bug修复" value="BUG" />
            <el-option label="重构优化" value="REFACTOR" />
            <el-option label="其他" value="TASK" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="taskForm.priority" placeholder="请选择">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="预估工时">
          <el-input-number v-model="taskForm.estimatedHours" :min="0.5" :step="0.5" />
          <span style="margin-left: 8px; color: #909399;">小时</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTask" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, More } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi } from '@/api'

const props = defineProps({
  projectId: [String, Number]
})

const emit = defineEmits(['refresh'])

const tasks = ref([])
const dialogVisible = ref(false)
const editingTask = ref(null)
const saving = ref(false)

const columns = [
  { status: 'TODO', title: '待办', color: '#909399' },
  { status: 'IN_PROGRESS', title: '进行中', color: '#409eff' },
  { status: 'REVIEW', title: '评审中', color: '#e6a23c' },
  { status: 'DONE', title: '已完成', color: '#67c23a' }
]

const taskForm = ref({
  title: '',
  description: '',
  taskType: 'FEATURE',
  priority: 'P2',
  estimatedHours: 4
})

const loadTasks = async () => {
  try {
    const res = await projectApi.getTasks(props.projectId)
    tasks.value = res.data || []
  } catch (error) {
    console.error('加载任务失败', error)
  }
}

const getColumnTasks = (status) => {
  return tasks.value.filter(t => t.status === status)
}

const showCreateDialog = () => {
  editingTask.value = null
  taskForm.value = {
    title: '',
    description: '',
    taskType: 'FEATURE',
    priority: 'P2',
    estimatedHours: 4
  }
  dialogVisible.value = true
}

const editTask = (task) => {
  editingTask.value = task
  taskForm.value = { ...task }
  dialogVisible.value = true
}

const saveTask = async () => {
  if (!taskForm.value.title) {
    ElMessage.warning('请输入任务标题')
    return
  }

  saving.value = true
  try {
    if (editingTask.value) {
      await projectApi.updateTask(editingTask.value.id, taskForm.value)
      ElMessage.success('更新成功')
    } else {
      await projectApi.createTask(props.projectId, taskForm.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadTasks()
    emit('refresh')
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

const moveTask = async (task, status) => {
  if (status === 'delete') {
    try {
      await ElMessageBox.confirm('确定要删除这个任务吗？', '确认')
      await projectApi.deleteTask(task.id)
      ElMessage.success('删除成功')
      loadTasks()
      emit('refresh')
    } catch (error) {
      if (error !== 'cancel') {
        ElMessage.error('删除失败')
      }
    }
  } else {
    try {
      await projectApi.updateTaskStatus(task.id, status)
      ElMessage.success('状态更新成功')
      loadTasks()
      emit('refresh')
    } catch (error) {
      ElMessage.error('更新失败')
    }
  }
}

const getPriorityType = (priority) => {
  const map = { P0: 'danger', P1: 'warning', P2: '', P3: 'info' }
  return map[priority] || ''
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.task-board {
  padding: 0 4px;
}

.board-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.board-header h3 {
  margin: 0;
}

.board-columns {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.board-column {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
  min-height: 400px;
}

.column-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 2px solid;
  margin-bottom: 12px;
}

.column-title {
  font-weight: 600;
  color: #303133;
}

.column-count {
  background: #e4e7ed;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  color: #606266;
}

.task-card {
  background: white;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}

.task-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.task-type {
  font-size: 11px;
  color: #909399;
}

.task-title {
  margin: 0 0 6px 0;
  font-size: 14px;
  color: #303133;
}

.task-desc {
  margin: 0 0 8px 0;
  font-size: 12px;
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.task-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-hours {
  font-size: 12px;
  color: #409eff;
}

.more-btn {
  cursor: pointer;
  color: #909399;
}

.more-btn:hover {
  color: #409eff;
}
</style>
