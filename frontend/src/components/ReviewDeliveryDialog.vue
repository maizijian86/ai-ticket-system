<template>
  <el-dialog
    :model-value="modelValue"
    title="验收交付"
    width="560px"
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
          <span class="summary-label">接单者</span>
          <span class="summary-value">{{ ticket.handlerName || '-' }}</span>
        </div>
        <div class="summary-row">
          <span class="summary-label">交付凭证</span>
          <span class="summary-value">
            <!--
              只用纯文本插值，绝不 v-html：这是接单者填的自由文本，会被存库，
              用 v-html 渲染等于把存储型 XSS 直接送到发布者浏览器里。
              渲染成链接时也只认 http(s)，挡掉 javascript: 之类的伪协议。
            -->
            <a
              v-if="isHttpUrl(ticket.completionProof)"
              :href="ticket.completionProof"
              target="_blank"
              rel="noopener noreferrer"
              class="proof-link"
            >{{ ticket.completionProof }}</a>
            <span v-else>{{ ticket.completionProof || '-' }}</span>
          </span>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="驳回理由（驳回时必填）" prop="reason">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            placeholder="说明哪里不符合要求，接单者会看到这段话并据此返工"
          />
        </el-form-item>
      </el-form>

      <el-alert type="warning" :closable="false" class="archive-note">
        <template #title>验收通过后</template>
        工单归档为「已完成」，并且<strong>本工单的项目沟通记录与 AI 开发助手的对话记忆
        会被一并清除</strong>。若还需要保留沟通内容，请先自行备份；驳回则不影响记录。
      </el-alert>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)" class="btn-glass">取消</el-button>
      <el-button type="danger" :loading="submitting" @click="submit(false)" class="btn-glass">
        驳回
      </el-button>
      <el-button type="primary" :loading="submitting" @click="submit(true)" class="btn-glass btn-glass-primary">
        验收通过
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
 * 验收弹窗 —— 发布者通过或驳回交付。
 *
 * 抽成组件是因为它至少有三个入口（工单详情、我的需求、消息）。三份拷贝最容易
 * 走样的有两处：一是「验收后沟通记录会被清除」的说明（必须与
 * TicketService.approveTicket 的真实行为一致），二是驳回理由必填（后端
 * ApproveTicketRequest 只注释约定、不加校验，前端不拦就直接 400）。
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
const form = reactive({ reason: '' })

// 驳回理由的"必填"只在点驳回时才成立，而 el-form 的 rules 是常驻的 ——
// 写进 rules 会导致点「验收通过」也被红字拦住。所以必填判断放在 submit 里。
const rules: FormRules = {
  reason: [{ max: 500, message: '驳回理由不要超过 500 字', trigger: 'blur' }]
}

/** 只认 http/https —— 交付凭证是用户自由填写并会渲染成链接的文本 */
function isHttpUrl(value?: string) {
  return !!value && /^https?:\/\/\S+/i.test(value.trim())
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.reason = ''
    formRef.value?.clearValidate()
  }
)

async function submit(approved: boolean) {
  if (!props.ticket) return
  if (!approved && !form.reason.trim()) {
    ElMessage.warning('驳回时必须填写理由，接单者需要知道改什么')
    return
  }
  submitting.value = true
  try {
    await ticketApi.approveTicket(props.ticket.id, {
      approved,
      reason: approved ? undefined : form.reason.trim()
    })
    ElMessage.success(approved ? '已验收通过，工单归档' : '已驳回，等待接单者返工')
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
  flex: 0 0 68px;
  color: var(--text-secondary);
}

.summary-value {
  color: var(--text-primary);
  word-break: break-all;
}

.proof-link {
  color: var(--accent-info);
  text-decoration: none;
}

.proof-link:hover {
  text-decoration: underline;
}

.archive-note {
  margin-top: 4px;
}
</style>
