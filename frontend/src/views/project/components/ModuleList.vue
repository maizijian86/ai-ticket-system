<template>
  <div class="module-list">
    <div class="list-header">
      <h3>模块管理</h3>
      <el-button type="primary" size="small" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        新建模块
      </el-button>
    </div>

    <div class="module-cards">
      <el-card v-for="module in modules" :key="module.id" class="module-card" shadow="hover">
        <div class="module-header">
          <h4>{{ module.name }}</h4>
          <el-dropdown @command="(cmd) => handleCommand(cmd, module)">
            <el-icon class="more-btn"><More /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <p class="module-desc">{{ module.description }}</p>
        <div class="module-progress">
          <div class="progress-info">
            <span>{{ module.completedTasks }}/{{ module.totalTasks }} 任务</span>
            <span>{{ module.progress }}%</span>
          </div>
          <el-progress :percentage="module.progress" :status="module.progress === 100 ? 'success' : ''" />
        </div>
        <el-tag :type="getStatusType(module.status)" size="small">{{ getStatusLabel(module.status) }}</el-tag>
      </el-card>

      <el-empty v-if="modules.length === 0" description="暂无模块" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingModule ? '编辑模块' : '新建模块'" width="400px">
      <el-form :model="moduleForm" label-width="80px">
        <el-form-item label="模块名称">
          <el-input v-model="moduleForm.name" placeholder="请输入模块名称" />
        </el-form-item>
        <el-form-item label="模块描述">
          <el-input v-model="moduleForm.description" type="textarea" :rows="3" placeholder="请输入模块描述" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="moduleForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveModule" :loading="saving">保存</el-button>
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

const modules = ref([])
const dialogVisible = ref(false)
const editingModule = ref(null)
const saving = ref(false)

const moduleForm = ref({
  name: '',
  description: '',
  sortOrder: 0
})

const loadModules = async () => {
  try {
    const res = await projectApi.getModules(props.projectId)
    modules.value = res.data || []
  } catch (error) {
    console.error('加载模块失败', error)
  }
}

const showCreateDialog = () => {
  editingModule.value = null
  moduleForm.value = { name: '', description: '', sortOrder: 0 }
  dialogVisible.value = true
}

const handleCommand = async (cmd, module) => {
  if (cmd === 'edit') {
    editingModule.value = module
    moduleForm.value = { ...module }
    dialogVisible.value = true
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm('确定要删除这个模块吗？模块下的任务将被解除关联', '确认')
      await projectApi.deleteModule(module.id)
      ElMessage.success('删除成功')
      loadModules()
      emit('refresh')
    } catch (error) {
      if (error !== 'cancel') {
        ElMessage.error('删除失败')
      }
    }
  }
}

const saveModule = async () => {
  if (!moduleForm.value.name) {
    ElMessage.warning('请输入模块名称')
    return
  }

  saving.value = true
  try {
    if (editingModule.value) {
      await projectApi.updateModule(editingModule.value.id, moduleForm.value)
      ElMessage.success('更新成功')
    } else {
      await projectApi.createModule(props.projectId, moduleForm.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadModules()
    emit('refresh')
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

const getStatusType = (status) => {
  const map = { PENDING: 'info', IN_PROGRESS: '', COMPLETED: 'success' }
  return map[status] || ''
}

const getStatusLabel = (status) => {
  const map = { PENDING: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成' }
  return map[status] || status
}

onMounted(() => {
  loadModules()
})
</script>

<style scoped>
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.list-header h3 {
  margin: 0;
}

.module-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.module-card {
  border-radius: 8px;
}

.module-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.module-header h4 {
  margin: 0;
  color: #303133;
}

.module-desc {
  color: #606266;
  font-size: 13px;
  margin: 0 0 12px 0;
}

.module-progress {
  margin-bottom: 12px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.more-btn {
  cursor: pointer;
  color: #909399;
}

.more-btn:hover {
  color: #409eff;
}
</style>
