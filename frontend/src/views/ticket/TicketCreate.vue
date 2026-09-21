<template>
  <div class="ticket-create">
    <div class="page-header">
      <el-button text @click="router.push('/tickets')" class="btn-glass">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <h2>发布项目需求</h2>
    </div>

    <div class="create-form">
      <el-card class="form-card glass-card">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="glass-form-item">

          <!-- 项目基本信息 -->
          <el-divider content-position="left">项目基本信息</el-divider>

          <el-form-item label="项目名称" prop="title" class="glass-input">
            <el-input
              v-model="form.title"
              placeholder="请输入项目名称，如：企业OA管理系统"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="项目类型" prop="projectType" class="glass-input">
            <el-select v-model="form.projectType" placeholder="请选择项目类型" style="width: 100%">
              <el-option label="JavaWeb项目" value="JavaWeb" />
              <el-option label="Python项目" value="Python" />
              <el-option label="前端项目" value="Frontend" />
              <el-option label="全栈项目" value="FullStack" />
              <el-option label="移动端项目" value="Mobile" />
              <el-option label="其他" value="Other" />
            </el-select>
          </el-form-item>

          <el-form-item label="技术栈" class="glass-input">
            <el-input
              v-model="form.techStack"
              placeholder="如：Spring Boot, MyBatis, Vue3, MySQL"
            />
          </el-form-item>

          <!-- 需求描述 -->
          <el-divider content-position="left">需求描述</el-divider>

          <el-form-item label="项目描述" prop="content" class="glass-input">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="4"
              placeholder="请简要描述项目背景和目标..."
              maxlength="2000"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="详细需求" class="glass-input">
            <el-input
              v-model="form.detailedRequirements"
              type="textarea"
              :rows="8"
              placeholder="请详细描述功能需求，包括：&#10;1. 核心功能模块&#10;2. 用户角色和权限&#10;3. 业务流程&#10;4. 数据要求&#10;5. 特殊要求..."
              maxlength="10000"
              show-word-limit
            />
          </el-form-item>

          <!--
            此处原有「AI智能分析」按钮。它调用的是 /v1/ai/classify —— 一个后端
            不存在的接口 —— 而且**丢弃了返回值**，无论成败都弹"AI分析完成"。
            也就是说它什么都不做，只给一个成功提示。PRD 实际是在工单详情页按需
            生成的（POST /v1/tickets/{id}/ai-analysis 与 /download-prd），与这里无关。
            因后端无对应能力且原逻辑有误导性，移除该入口。
          -->

          <!-- 预算和时间 -->
          <el-divider content-position="left">预算与时间</el-divider>

          <el-form-item label="预算（元）" class="glass-input">
            <el-input-number
              v-model="form.budget"
              :min="0"
              :step="100"
              placeholder="请输入预算"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="截止日期" class="glass-input">
            <el-date-picker
              v-model="form.deadline"
              type="date"
              placeholder="选择截止日期"
              style="width: 100%"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>

          <el-form-item label="紧急程度" class="glass-radio">
            <el-radio-group v-model="form.urgency">
              <el-radio label="LOW">低</el-radio>
              <el-radio label="NORMAL">普通</el-radio>
              <el-radio label="HIGH">紧急</el-radio>
              <el-radio label="CRITICAL">非常紧急</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>

        <div class="form-actions">
          <el-button @click="router.push('/tickets')" class="btn-glass">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit" class="btn-glass btn-glass-primary">
            发布项目
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ticketApi } from '@/api'
import { useTicketStore } from '@/stores'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import type { TicketCategory, Urgency } from '@/types'

const router = useRouter()
const ticketStore = useTicketStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  title: '',
  content: '',
  category: 'OTHER' as TicketCategory | '',
  urgency: 'NORMAL' as Urgency,
  projectType: '',
  techStack: '',
  detailedRequirements: '',
  budget: null as number | null,
  deadline: ''
})

const rules = {
  title: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  content: [{ required: true, message: '请输入项目描述', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }]
}

async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        const ticket = await ticketApi.createTicket({
          title: form.title,
          content: form.content,
          category: form.category as TicketCategory,
          urgency: form.urgency as Urgency,
          projectType: form.projectType,
          techStack: form.techStack,
          detailedRequirements: form.detailedRequirements,
          budget: form.budget,
          deadline: form.deadline
        })

        ticketStore.triggerRefresh()
        ElMessage.success('项目发布成功')
        router.push(`/tickets/${ticket.id}`)
      } finally {
        submitting.value = false
      }
    }
  })
}
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.create-form {
  max-width: 800px;
}

.form-card {
  padding: 8px;
}

.ai-fill-item {
  margin-bottom: 20px;
}

.ai-fill-btn {
  margin-right: 16px;
}

.ai-hint {
  color: var(--text-secondary, #909399);
  font-size: 13px;
}

.ai-hint.success {
  color: var(--el-color-success, #67c23a);
}

.form-actions {
  margin-top: 32px;
  display: flex;
  justify-content: flex-end;
  gap: 16px;
}
</style>
