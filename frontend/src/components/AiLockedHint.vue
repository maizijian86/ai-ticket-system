<template>
  <!--
    AI 功能的"用不了"提示块。三种用不了的原因（未登录 / 不是接单人 / 正在处理中）
    长得一样、只是文案不同，抽出来是为了让四个 tab 的遮罩不会各写一份 ——
    各写一份的结果是四种稍微不一样的灰框，用户会以为是四种不同的错误。
  -->
  <div class="ai-locked">
    <div class="ai-locked-icon">
      <el-icon size="24"><Lock /></el-icon>
    </div>
    <div class="ai-locked-title">{{ title }}</div>
    <div v-if="detail" class="ai-locked-detail">{{ detail }}</div>
    <el-button
      v-if="actionText"
      type="primary"
      size="small"
      class="btn-glass btn-glass-primary"
      @click="emit('action')"
    >
      {{ actionText }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'

defineProps<{
  title: string
  detail?: string
  /** 有值时渲染一个按钮，点击抛 action 事件（目前只用于"去登录"） */
  actionText?: string
}>()

const emit = defineEmits<{ action: [] }>()
</script>

<style scoped>
.ai-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 44px 20px;
  text-align: center;
}

.ai-locked-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: 50%;
  margin-bottom: 4px;
}

.ai-locked-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-secondary);
}

.ai-locked-detail {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-tertiary);
  max-width: 380px;
}
</style>
