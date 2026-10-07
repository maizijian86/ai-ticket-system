<template>
  <el-dialog
    :model-value="modelValue"
    title="确认接单"
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
        <div class="summary-row">
          <span class="summary-label">预算</span>
          <span class="summary-value">{{ ticket.budget ? `¥${ticket.budget}` : '面议' }}</span>
        </div>
        <div class="summary-row">
          <span class="summary-label">截止日期</span>
          <span class="summary-value">{{ ticket.deadline ? formatDate(ticket.deadline) : '待定' }}</span>
        </div>
        <div class="summary-row">
          <span class="summary-label">技术栈</span>
          <span class="summary-value">{{ ticket.techStack || '未指定' }}</span>
        </div>
      </div>

      <!--
        提示的措辞不能提「待处理」这个菜单 —— 那个页面已经并进需求大厅了。
        现在该做的是在大厅的「进行中」卡里提交交付凭证。
      -->
      <el-alert type="warning" :closable="false" class="archive-note">
        <template #title>接单后</template>
        工单会立即从大厅下架并归属到你名下，其他开发者将无法再接。此后你需要主动
        推进，并在需求大厅的「进行中」里提交交付凭证，发布者验收通过才算完成。
      </el-alert>

      <el-checkbox v-model="confirmed" class="pickup-confirm">
        我已确认需求范围与截止时间，愿意承接此项目
      </el-checkbox>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)" class="btn-glass">取消</el-button>
      <el-button
        type="success"
        :disabled="!confirmed"
        :loading="submitting"
        @click="submit"
        class="btn-glass btn-glass-success"
      >
        确认接单
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 接单二次确认弹窗。
 *
 * 从 TicketDetail.vue 抽出来的 —— 大厅的工单卡片上也要有「接单」按钮，而接单是
 * 不可逆的（接了就下架、就归属到你名下），值得一个正经的确认框，不能在大厅里
 * 退化成一个 ElMessageBox。
 *
 * props / emits 与 CompleteDeliveryDialog、ReviewDeliveryDialog 保持同一套契约
 * （modelValue / ticket 进来，update:modelValue / done 出去），三个弹窗在调用点
 * 长得一样，读的人不用记两套。
 */
import { ref, watch } from 'vue'
import { ticketApi } from '@/api'
import { ElMessage } from 'element-plus'
import type { TicketDTO } from '@/types'

const props = defineProps<{
  modelValue: boolean
  ticket: TicketDTO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'done'): void
}>()

const confirmed = ref(false)
const submitting = ref(false)

// 每次打开都重置勾选：上一次点开的勾选态留着，会让"我确认过了"变成假的。
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) confirmed.value = false
  }
)

async function submit() {
  if (!props.ticket || !confirmed.value) return
  submitting.value = true
  try {
    await ticketApi.pickupTicket(props.ticket.id)
    // 只发 done，不在这里调 ticketStore.triggerRefresh() —— 与另两个交付弹窗
    // 保持同一套契约：弹窗只负责"做完了"，刷新由调用方决定。调用方各自知道
    // 自己该刷什么（详情页要 loadTicket，大厅要重拉列表和统计）。
    ElMessage.success('接单成功！请进入聊天与发布者沟通')
    emit('update:modelValue', false)
    emit('done')
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

function formatDate(date: string) {
  return new Date(date).toLocaleString('zh-CN')
}
</script>

<style scoped>
/* 这三个类与另两个弹窗里的同名样式一致，见各自的 .summary 块 */
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

.archive-note {
  margin-top: 4px;
}

.pickup-confirm {
  margin-top: 14px;
}
</style>
