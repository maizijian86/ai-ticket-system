<template>
  <div class="project-create">
    <el-card class="create-card">
      <template #header>
        <div class="card-header">
          <span>创建新项目</span>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入项目名称" />
        </el-form-item>

        <el-form-item label="项目描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="6"
            placeholder="请详细描述项目需求，例如：开发一个学生信息管理系统，包含学生管理、课程管理、成绩管理等功能..."
          />
        </el-form-item>

        <el-form-item label="项目类型" prop="projectType">
          <el-select v-model="form.projectType" placeholder="请选择项目类型">
            <el-option label="毕业设计" value="THESIS" />
            <el-option label="外包项目" value="OUTSOURCE" />
            <el-option label="内部项目" value="INTERNAL" />
            <el-option label="个人项目" value="PERSONAL" />
          </el-select>
        </el-form-item>

        <el-form-item label="截止日期" prop="deadline">
          <el-date-picker
            v-model="form.deadline"
            type="date"
            placeholder="选择截止日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" placeholder="请选择优先级">
            <el-option label="P0 - 紧急" value="P0" />
            <el-option label="P1 - 高" value="P1" />
            <el-option label="P2 - 中" value="P2" />
            <el-option label="P3 - 低" value="P3" />
          </el-select>
        </el-form-item>

        <!--
          此处原有「AI 智能分析」按钮及分析结果 / 任务分解两张卡片。它们依赖
          /v1/ai/analyze-project 与 /v1/ai/decompose-tasks，两个接口后端都不存在
          （ai-service 只有 /generate-prd 和 /dev-advice），点击必然 404，因此
          分析结果永远为空、两张卡片永远不渲染。后端补齐接口后再恢复此入口。
        -->

        <el-divider />

        <el-form-item>
          <el-button type="primary" @click="submitForm" :loading="submitting">
            创建项目
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { projectApi } from '@/api'
import type { Priority, ProjectType } from '@/types'

const router = useRouter()
const formRef = ref<FormInstance | null>(null)
const submitting = ref(false)

// projectType 用 '' 表示「还没选」，所以类型里要带上它 —— 后端要的是非空的
// ProjectType，中间态不该混进请求体（下面提交时会收敛掉）。
const form = reactive<{
  name: string
  description: string
  projectType: ProjectType | ''
  deadline: string
  priority: Priority
}>({
  name: '',
  description: '',
  projectType: '',
  deadline: '',
  priority: 'P2'
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入项目描述', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }]
}

const submitForm = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 表单校验只保证运行时非空，类型上仍是 ProjectType | ''。收敛成局部常量，
  // 既让类型变窄，也避免用 as 把 '' 强行当成合法枚举值传下去。
  const projectType = form.projectType
  if (!projectType) {
    ElMessage.warning('请选择项目类型')
    return
  }

  submitting.value = true
  try {
    // projectApi 的响应拦截器返回的是后端信封 { code, message, data }，
    // 新建的项目在 res.data 上。写成 res.id 会得到 undefined，
    // 跳转到 /projects/undefined，详情页随即加载失败。
    const res = await projectApi.create({
      name: form.name,
      description: form.description,
      projectType,
      deadline: form.deadline,
      priority: form.priority
    })
    ElMessage.success('项目创建成功')
    router.push(`/projects/${res.data.id}`)
  } catch (error) {
    ElMessage.error('创建失败: ' + (error instanceof Error ? error.message : '未知错误'))
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  formRef.value?.resetFields()
}
</script>

<style scoped>
.project-create {
  max-width: 900px;
  margin: 20px auto;
  padding: 0 20px;
}

.create-card {
  border-radius: 8px;
}

.card-header {
  font-size: 18px;
  font-weight: 600;
}

.ai-tip {
  margin-left: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.ai-result-card,
.task-result-card {
  margin-top: 16px;
  background: var(--glass-bg);
}

.ai-result-title,
.task-result-title {
  font-weight: 600;
  color: var(--accent-primary);
}

.ai-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}

.module-item {
  margin-bottom: 20px;
  padding: 12px;
  background: var(--glass-bg);
  border-radius: 6px;
}

.module-item h4 {
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.module-desc {
  color: var(--text-secondary);
  font-size: 13px;
  margin: 0 0 12px 0;
}

.task-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.task-list li {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--glass-bg);
  border-radius: 4px;
  margin-bottom: 6px;
}

.task-title {
  color: var(--text-primary);
}

.task-hours {
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
