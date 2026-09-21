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

    <el-dialog v-model="dialogVisible" :title="editingMilestone ? '编辑里程碑' : '新建里程碑'" width="400px">
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

<script setup>
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectApi } from '@/api'

const props = defineProps({
  projectId: [String, Number]
})

const milestones = ref([])
const dialogVisible = ref(false)
const editingMilestone = ref(null)
const saving = ref(false)

const milestoneForm = ref({
  name: '',
  description: '',
  dueDate: '',
  sortOrder: 0
})

const loadMilestones = async () => {
  try {
    const res = await projectApi.getMilestones(props.projectId)
    milestones.value = res.data || []
  } catch (error) {
    console.error('加载里程碑失败', error)
  }
}

const showCreateDialog = () => {
  editingMilestone.value = null
  milestoneForm.value = { name: '', description: '', dueDate: '', sortOrder: 0 }
  dialogVisible.value = true
}

const editMilestone = (milestone) => {
  editingMilestone.value = milestone
  milestoneForm.value = { ...milestone }
  dialogVisible.value = true
}

const saveMilestone = async () => {
  if (!milestoneForm.value.name) {
    ElMessage.warning('请输入里程碑名称')
    return
  }

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

const completeMilestone = async (milestone) => {
  try {
    await projectApi.completeMilestone(milestone.id)
    ElMessage.success('里程碑已完成')
    loadMilestones()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const deleteMilestone = async (milestone) => {
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

const getStatusType = (status) => {
  const map = { PENDING: 'info', ACHIEVED: 'success', MISSED: 'danger' }
  return map[status] || ''
}

const getStatusLabel = (status) => {
  const map = { PENDING: '待完成', ACHIEVED: '已达成', MISSED: '已逾期' }
  return map[status] || status
}

const formatDate = (date) => {
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
  color: #303133;
}

.milestone-desc {
  color: #606266;
  font-size: 13px;
  margin: 0 0 12px 0;
}

.milestone-actions {
  display: flex;
  gap: 8px;
}
</style>
