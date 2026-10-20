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
    <el-button v-if="actionText" type="primary" size="small" @click="emit('action')">
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

/* 锁图标收在一枚浅灰圆里。原来是深色玻璃圆 —— 现在整站是白底，一个深色圆
   会变成"这是错误"的重音；这里的语义只是"还没到那一步"，用中性面就够。 */
.ai-locked-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ds-ink);
  opacity: 0.6;
  background: var(--ds-surface-soft);
  border-radius: var(--ds-r-full);
  margin-bottom: 4px;
}

.ai-locked-title {
  font-size: 15px;
  font-weight: var(--ds-fw-semi);
  color: var(--ds-ink);
}

.ai-locked-detail {
  font-size: 13px;
  font-weight: var(--ds-fw-light);
  line-height: 1.6;
  color: var(--ds-ink);
  opacity: 0.7;
  max-width: 380px;
}
</style>
