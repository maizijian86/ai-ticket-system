<template>
  <el-dialog
    :model-value="modelValue"
    title="提交交付凭证"
    width="520px"
    class="glass-dialog"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="ticket" class="dialog-body">
      <div class="summary">
        <div class="summary-row">
          <span class="summary-label">工单</span>
          <span class="summary-value">{{ ticket.title }}</span>
        </div>
        <div class="summary-row">
          <span class="summary-label">发布者</span>
          <span class="summary-value">{{ ticket.creatorName || '-' }}</span>
        </div>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="交付凭证（仓库地址 / 项目官网链接）" prop="completionProof">
          <el-input
            v-model="form.completionProof"
            placeholder="https://github.com/你的账号/项目名"
            clearable
          />
          <div class="field-hint">
            发布者会看到这个链接并据此验收。线上没有统一的交付平台，交付一个可访问的
            仓库或站点地址即可。
          </div>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" class="archive-note">
        <template #title>提交后</template>
        工单将转入「待验收」，发布者可以验收通过或驳回。验收通过后工单归档，
        <strong>本工单的项目沟通记录与 AI 开发助手的对话记忆会一并清除</strong>，
        请在此之前确认资料已交付完整。
      </el-alert>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)" class="btn-glass">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit" class="btn-glass btn-glass-primary">
        提交交付
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ticketApi } from '@/api'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { TicketDTO } from '@/types'

/**
 * 「去完成」弹窗 —— 接单者填交付凭证。
 *
 * 抽成组件是因为它有三个入口：工单详情、待处理菜单、以及后续可能的消息入口。
 * 三份拷贝最容易走样的正是那段「验收后沟通记录会被清除」的说明 —— 而那句话
 * 必须和 TicketService.approveTicket 的真实行为一致，不能让用户在一个入口
 * 看到承诺、在另一个入口看不到。
 */
const props = defineProps<{
  modelValue: boolean
  ticket: TicketDTO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'done'): void
}>()

const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ completionProof: '' })

const rules: FormRules = {
  completionProof: [
    { required: true, message: '请填写交付凭证', trigger: 'blur' },
    {
      // 只做"看起来是个可访问地址"的校验。不深入校验域名 —— 交付方式本来
      // 就不限于 GitHub（项目官网、网盘、演示站都可能），管太死反而堵住正路。
      validator: (_rule, value: string, callback) => {
        const v = (value || '').trim()
        if (v && !/^https?:\/\/\S+\.\S+/.test(v)) {
          callback(new Error('请填写以 http:// 或 https:// 开头的完整地址'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

// 每次打开都把上一次的输入和残留的校验红字清干净
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.completionProof = props.ticket?.completionProof || ''
    formRef.value?.clearValidate()
  }
)

async function submit() {
  if (!props.ticket || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await ticketApi.completeTicket(props.ticket.id, {
      completionProof: form.completionProof.trim()
    })
    ElMessage.success('已提交交付，等待发布者验收')
    emit('update:modelValue', false)
    emit('done')
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.summary {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--glass-bg, rgba(255, 255, 255, 0.05));
}

.summary-row {
  display: flex;
  gap: 12px;
  line-height: 1.9;
  font-size: 13px;
}

.summary-label {
  flex: 0 0 56px;
  color: var(--text-secondary);
}

.summary-value {
  color: var(--text-primary);
  word-break: break-all;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.archive-note {
  margin-top: 4px;
}
</style>
