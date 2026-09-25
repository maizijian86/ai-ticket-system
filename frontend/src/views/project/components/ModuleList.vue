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
          <el-dropdown @command="(cmd: string | number | object) => handleCommand(cmd, module)">
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

    <el-dialog v-model="dialogVisible" :title="editingModule ? '编辑模块' : '新建模块'" width="400px" class="glass-dialog">
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

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, More } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi } from '@/api'
import type { CreateModuleRequest, ModuleStatus, ProjectModuleDTO } from '@/types'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const props = defineProps<{ projectId?: number }>()

const emit = defineEmits<{ refresh: [] }>()

const modules = ref<ProjectModuleDTO[]>([])
const dialogVisible = ref(false)
const editingModule = ref<ProjectModuleDTO | null>(null)
const saving = ref(false)

/**
 * 表单只覆盖对话框真正能让用户改的字段（CreateModuleRequest 里还有 status /
 * progress / totalTasks / completedTasks，那四个由后端随任务进度算，不属于用户输入）。
 */
type ModuleForm = Pick<CreateModuleRequest, 'name' | 'description' | 'sortOrder'>

const emptyForm = (): ModuleForm => ({ name: '', description: '', sortOrder: 0 })

const moduleForm = ref<ModuleForm>(emptyForm())

const loadModules = async () => {
  if (!props.projectId) return
  try {
    const res = await projectApi.getModules(props.projectId)
    modules.value = res.data || []
  } catch (error) {
    console.error('加载模块失败', error)
  }
}

const showCreateDialog = () => {
  editingModule.value = null
  moduleForm.value = emptyForm()
  dialogVisible.value = true
}

// el-dropdown 的 command 事件签名是 [String, Object, Number]，而这里的指令值全是字符串。
// 收敛成 string 再比较，省得让联合类型渗进业务判断。
const handleCommand = async (raw: string | number | object, module: ProjectModuleDTO) => {
  const cmd = String(raw)
  if (cmd === 'edit') {
    editingModule.value = module
    // 只挑对话框能编辑的字段，不要 { ...module } 展开：展开会把 status / progress /
    // totalTasks 一起发出去，而这三个是后端按任务数算出来的，不是用户输入。
    // 后端 updateModule 是 if (x != null) 才写，不发即保持原值。
    moduleForm.value = {
      name: module.name,
      description: module.description,
      sortOrder: module.sortOrder
    }
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
  if (!props.projectId) return

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

/** 与后端 ProjectService 里 module.setStatus(...) 的取值一一对应。 */
const statusTagTypes: Record<ModuleStatus, TagType | ''> = {
  PENDING: 'info',
  IN_PROGRESS: '',
  COMPLETED: 'success'
}

const statusLabels: Record<ModuleStatus, string> = {
  PENDING: '待开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成'
}

// 查表失败时回退显示原始值：后端若新增了状态，界面显示原始字符串比显示空白更容易定位。
const getStatusType = (status?: ModuleStatus) => (status ? statusTagTypes[status] : '')
const getStatusLabel = (status?: ModuleStatus) => (status ? statusLabels[status] || status : '-')

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
