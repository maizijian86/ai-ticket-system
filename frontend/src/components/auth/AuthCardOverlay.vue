<template>
  <!--
    Teleport 到 body：浮层是 fixed 定位的，留在 AppLayout 的层级里会被布局上的
    transform / overflow 影响（比如侧边栏的 position: sticky 容器），
    挪到 body 下就与页面结构无关了。
  -->
  <Teleport to="body">
    <div v-if="authStore.authCardVisible" class="auth-card-layer">
      <div class="auth-card-stage">
        <button class="auth-card-close" type="button" title="关闭" @click="authStore.closeAuthCard()">
          <el-icon size="16"><Close /></el-icon>
        </button>
        <LoginCard v-if="authStore.authCardMode === 'login'" />
        <RegisterCard v-else />
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores'
import LoginCard from './LoginCard.vue'
import RegisterCard from './RegisterCard.vue'

const authStore = useAuthStore()
</script>

<style scoped>
/**
 * 这一层是**固定铺满视口但透明且不吃事件**的居中容器 —— 不是常见的全屏遮罩。
 *
 * 关键在 `pointer-events: none`：需求要的是"卡片显示在屏幕中间、背后的页面还能看
 * 还能滚"，所以浮层不能拦截点击与滚轮。事件只由卡片自己（.auth-card-stage 上的
 * `pointer-events: auto`）接收。
 *
 * 副作用是"点背景关闭"做不到 —— 背景根本不收点击。这是刻意的：让背景可点，
 * 就等于让用户在滚动页面时先被卡片吃掉一次点击。关闭统一走右上角的 X。
 */
.auth-card-layer {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  pointer-events: none;
  /* 背景不加任何 background-color / 模糊：背后的页面必须清晰可见 */
}

.auth-card-stage {
  position: relative;
  display: flex;
  max-height: calc(100vh - 48px);
  pointer-events: auto;
  /*
   * 阴影用 box-shadow 而不是 filter: drop-shadow()。
   *
   * 这不是风格选择：**祖先元素只要带 filter，就会成为 backdrop-filter 的
   * "背景根"**，卡片自己的 backdrop-filter 于是只去采样这个空的阴影层（什么都
   * 采样不到），模糊彻底失效 —— 实测表现是卡片背后的大厅文字锐利地透过来，
   * 卡片本身反而看不清。换成 box-shadow 不产生这一层，玻璃模糊就回来了。
   */
  border-radius: var(--radius-lg);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
}

/*
 * 卡片是浮层里唯一可点的东西，`.glass-card:hover` 那套"悬停上浮 + 提亮"在这里
 * 只会让对话框在鼠标划过时抖一下。用 `:deep` 提高特异性压掉它（父组件的 scoped
 * 选择器加上 :deep 后特异性高于 .glass-card:hover，与注入顺序无关）。
 */
.auth-card-stage :deep(.auth-card:hover) {
  transform: none;
  background: var(--glass-bg);
  border-color: var(--glass-border);
  box-shadow: none;
}

.auth-card-close {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 2;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s ease;
}

.auth-card-close:hover {
  color: var(--text-primary);
  background: var(--glass-bg-hover);
  border-color: var(--glass-border-hover);
  transform: rotate(90deg);
}
</style>
