<template>
  <div class="milestone-list">
    <div class="list-header">
      <h3>里程碑</h3>
      <el-button type="primary" size="small" @click="showCreateDialog">
        <el-icon><Plus /></el-icon>
        新建里程碑
      </el-button>
    </div>

    <el-timeline>
      <el-timeline-item
        v-for="milestone in milestones"
        :key="milestone.id"
        :type="getStatusType(milestone.status)"
        :hollow="milestone.status === 'PENDING'"
        :timestamp="formatDate(milestone.dueDate)"
        placement="top"
      >
        <el-card class="milestone-card">
          <div class="milestone-header">
            <h4>{{ milestone.name }}</h4>
            <el-tag :type="getStatusType(milestone.status)" size="small">
              {{ getStatusLabel(milestone.status) }}
            </el-tag>
          </div>
          <p class="milestone-desc">{{ milestone.description }}</p>
          <div class="milestone-actions">
            <el-button v-if="milestone.status === 'PENDING'" type="success" size="small"
              @click="completeMilestone(milestone)">
              标记完成
            </el-button>
            <el-button text size="small" @click="editMilestone(milestone)">编辑</el-button>
            <el-button text size="small" type="danger" @click="deleteMilestone(milestone)">删除</el-button>
          </div>
        </el-card>
      </el-timeline-item>
    </el-timeline>

    <el-empty v-if="milestones.length === 0" description="暂无里程碑" />

    <el-dialog v-model="dialogVisible" :title="editingMilestone ? '编辑里程碑' : '新建里程碑'" width="400px" class="glass-dialog">
      <el-form :model="milestoneForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="milestoneForm.name" placeholder="请输入里程碑名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="milestoneForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="milestoneForm.dueDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="milestoneForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMilestone" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi } from '@/api'
import type { CreateMilestoneRequest, MilestoneDTO, MilestoneStatus } from '@/types'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const props = defineProps<{ projectId?: number }>()

const milestones = ref<MilestoneDTO[]>([])
const dialogVisible = ref(false)
const editingMilestone = ref<MilestoneDTO | null>(null)
const saving = ref(false)

/**
 * 表单只覆盖对话框真正能让用户改的字段（CreateMilestoneRequest 里还有
 * status / actualDate，它们由 completeMilestone 等专用接口维护，不该出现在表单里）。
 */
type MilestoneForm = Pick<CreateMilestoneRequest, 'name' | 'description' | 'dueDate' | 'sortOrder'>

const emptyForm = (): MilestoneForm => ({ name: '', description: '', dueDate: '', sortOrder: 0 })

const milestoneForm = ref<MilestoneForm>(emptyForm())

const loadMilestones = async () => {
  if (!props.projectId) return
  try {
    const res = await projectApi.getMilestones(props.projectId)
    milestones.value = res.data || []
  } catch (error) {
    console.error('加载里程碑失败', error)
  }
}

const showCreateDialog = () => {
  editingMilestone.value = null
  milestoneForm.value = emptyForm()
  dialogVisible.value = true
}

const editMilestone = (milestone: MilestoneDTO) => {
  editingMilestone.value = milestone
  // 只挑对话框能编辑的字段，不要 { ...milestone } 展开：展开会把 status / actualDate
  // 一起发出去，而这两个字段归 completeMilestone 管，不该由编辑表单覆盖。
  // 后端 updateMilestone 是 if (x != null) 才写，不发即保持原值。
  milestoneForm.value = {
    name: milestone.name,
    description: milestone.description,
    dueDate: milestone.dueDate,
    sortOrder: milestone.sortOrder
  }
  dialogVisible.value = true
}

const saveMilestone = async () => {
  if (!milestoneForm.value.name) {
    ElMessage.warning('请输入里程碑名称')
    return
  }
  if (!props.projectId) return

  saving.value = true
  try {
    if (editingMilestone.value) {
      await projectApi.updateMilestone(editingMilestone.value.id, milestoneForm.value)
      ElMessage.success('更新成功')
    } else {
      await projectApi.createMilestone(props.projectId, milestoneForm.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadMilestones()
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

const completeMilestone = async (milestone: MilestoneDTO) => {
  try {
    await projectApi.completeMilestone(milestone.id)
    ElMessage.success('里程碑已完成')
    loadMilestones()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const deleteMilestone = async (milestone: MilestoneDTO) => {
  try {
    await ElMessageBox.confirm('确定要删除这个里程碑吗？', '确认')
    await projectApi.deleteMilestone(milestone.id)
    ElMessage.success('删除成功')
    loadMilestones()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 后端标记完成时写的是 ACHIEVED（ProjectService.completeMilestone），不是 COMPLETED。
// 这里的键必须与后端一致，否则里程碑完成后状态标签会退化成原始字符串。
const statusTagTypes: Record<MilestoneStatus, TagType> = {
  PENDING: 'info',
  ACHIEVED: 'success',
  MISSED: 'danger'
}

const statusLabels: Record<MilestoneStatus, string> = {
  PENDING: '待完成',
  ACHIEVED: '已达成',
  MISSED: '已逾期'
}

// 查表失败时回退显示原始值：后端若新增了状态，界面显示原始字符串比显示空白更容易定位。
// 查表失败时回退成 'info'（灰）而不是像其他几个文件那样回退成 ''（蓝）：这里的返回值
// 同时喂给 el-timeline-item 的 :type，是时间轴节点的颜色，缺失状态下灰色比蓝色更贴切。
const getStatusType = (status?: MilestoneStatus) => (status ? statusTagTypes[status] : 'info')
const getStatusLabel = (status?: MilestoneStatus) => (status ? statusLabels[status] || status : '-')

const formatDate = (date?: string) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

onMounted(() => {
  loadMilestones()
})
</script>

<style scoped>
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.list-header h3 {
  margin: 0;
}

.milestone-card {
  margin-bottom: 0;
}

.milestone-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.milestone-header h4 {
  margin: 0;
  color: var(--text-primary);
}

.milestone-desc {
  color: var(--text-secondary);
  font-size: 13px;
  margin: 0 0 12px 0;
}

.milestone-actions {
  display: flex;
  gap: 8px;
}
</style>
