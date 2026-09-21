<template>
  <div class="ticket-edit">
    <div class="page-header">
      <el-button text @click="router.push(`/tickets/${ticketId}`)" class="btn-glass">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <h2>编辑工单</h2>
    </div>

    <div class="edit-form" v-loading="loading">
      <el-card class="form-card glass-card" v-if="form.content !== undefined">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="glass-form-item">
          <el-form-item label="问题描述" prop="content" class="glass-input">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="6"
              placeholder="请详细描述您遇到的问题..."
              maxlength="2000"
              show-word-limit
            />
          </el-form-item>

          <!--
            此处原有「AI智能填充」按钮，调用 /v1/ai/classify、/v1/ai/priority、
            /v1/ai/generate-title 三个接口。这三个接口在后端并不存在
            （ai-service 只实现了 /generate-prd 和 /dev-advice），一次调用就是 404，
            按钮的唯一效果是弹一句"AI填充失败"。因后端无对应能力，先移除该入口。
          -->

          <el-form-item label="标题" prop="title" class="glass-input">
            <el-input
              v-model="form.title"
              placeholder="请输入工单标题"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="分类" prop="category" class="glass-input">
            <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
              <el-option label="Bug - 程序错误" value="BUG" />
              <el-option label="咨询 - 使用咨询" value="CONSULT" />
              <el-option label="投诉 - 问题投诉" value="COMPLAINT" />
              <el-option label="建议 - 改进建议" value="SUGGESTION" />
              <el-option label="其他" value="OTHER" />
            </el-select>
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
          <el-button @click="router.push(`/tickets/${ticketId}`)" class="btn-glass">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit" class="btn-glass btn-glass-primary">
            保存修改
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ticketApi } from '@/api'
import { useTicketStore } from '@/stores'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import type { TicketCategory, Urgency, TicketDTO } from '@/types'

const router = useRouter()
const route = useRoute()
const ticketStore = useTicketStore()

const ticketId = Number(route.params.id)

const formRef = ref<FormInstance>()
const submitting = ref(false)
const loading = ref(false)

const form = reactive({
  content: '',
  title: '',
  category: '' as TicketCategory | '',
  urgency: 'NORMAL' as Urgency
})

const rules = {
  content: [{ required: true, message: '请输入问题描述', trigger: 'blur' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

async function loadTicket() {
  loading.value = true
  try {
    const ticket: TicketDTO = await ticketApi.getTicket(ticketId)
    form.content = ticket.content
    form.title = ticket.title
    form.category = ticket.category || ''
    form.urgency = ticket.urgency
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        await ticketApi.updateTicket(ticketId, {
          title: form.title,
          content: form.content,
          category: form.category as TicketCategory,
          urgency: form.urgency as Urgency
        } as Partial<TicketDTO>)

        ticketStore.triggerRefresh()
        ElMessage.success('工单已更新')
        router.push(`/tickets/${ticketId}`)
      } finally {
        submitting.value = false
      }
    }
  })
}

onMounted(() => {
  loadTicket()
})
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

.edit-form {
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
