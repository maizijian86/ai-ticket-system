<template>
  <div class="auth-card glass-card">
    <div class="auth-card-header">
      <div class="logo-icon">
        <el-icon size="30"><Ticket /></el-icon>
      </div>
      <h1>AI 智能工单系统</h1>
      <p class="subtitle">智能分类 / 自动派单 / RAG 回复</p>
    </div>

    <!--
      活动位。登录卡片是"未登录用户停留时间最长的一块界面"，把赠送额度说在这里
      比只放在大厅横幅上更有可能被看到。
    -->
    <div class="promo-strip">
      <el-icon size="14"><Present /></el-icon>
      <span>{{ SIGNUP_PROMO_TITLE }}</span>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" class="auth-form glass-form-item" @submit.prevent="handleLogin">
      <el-form-item prop="username" class="glass-input">
        <el-input
          v-model="form.username"
          placeholder="用户名"
          size="large"
          :prefix-icon="User"
        />
      </el-form-item>
      <el-form-item prop="password" class="glass-input">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="密码"
          size="large"
          :prefix-icon="Lock"
          show-password
          @keyup.enter="handleLogin"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="auth-submit-btn"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form-item>
    </el-form>

    <div class="auth-card-footer">
      <span>还没有账号？</span>
      <!-- 卡片内部互相切换，不再跳路由 —— /login、/register 两个整页路由已删除 -->
      <a href="javascript:void(0)" @click="authStore.switchAuthCard('register')">立即注册</a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores'
import { User, Lock, Ticket, Present } from '@element-plus/icons-vue'
import { SIGNUP_PROMO_TITLE } from '@/utils/ai-quota'
import type { FormInstance, FormRules } from 'element-plus'

const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // 登录成功后由 store 统一关掉卡片，这里不再管浮层的显隐。
        // 也不再 router.push('/tickets')：卡片可能是在任意页面被打开的，
        // 强行跳走会把用户从他正在看的内容上拽开（守卫已经把未登录用户
        // 送到大厅了，那里就是最合适的位置）。
        await authStore.login(form.username, form.password)
      } catch {
        // 错误提示由 axios 拦截器统一弹出，这里吞掉即可
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
/*
 * 样式来自原 views/auth/Login.vue，去掉的是"整页"那一层：100vh 的容器和三个
 * 全屏背景光球。卡片自身的观感（glass-card、渐变 logo、渐变按钮、输入框覆盖）
 * 原样保留。
 */
.auth-card {
  width: 400px;
  max-width: 100%;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  padding: 36px 32px;
  position: relative;
  animation: cardIn 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.auth-card-header {
  text-align: center;
  margin-bottom: 20px;
}

.logo-icon {
  width: 60px;
  height: 60px;
  margin: 0 auto 14px;
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary), var(--accent-tertiary));
  background-size: 200% 200%;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 8px 32px rgba(0, 122, 255, 0.4);
  animation: gradientShift 4s ease infinite;
}

@keyframes gradientShift {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

.auth-card-header h1 {
  font-size: 21px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
  letter-spacing: -0.5px;
}

.subtitle {
  font-size: 13px;
  color: var(--text-tertiary);
  letter-spacing: 1.5px;
}

.promo-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 20px;
  padding: 9px 12px;
  font-size: 13px;
  font-weight: 500;
  color: var(--accent-warning);
  background: rgba(255, 159, 10, 0.12);
  border: 1px solid rgba(255, 159, 10, 0.3);
  border-radius: var(--radius-md);
}

.auth-form {
  margin-bottom: 20px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-submit-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary)) !important;
  border: none !important;
  color: #fff;
  box-shadow: 0 4px 20px rgba(0, 122, 255, 0.4);
  transition: all 0.3s ease;
}

.auth-submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(0, 122, 255, 0.5);
}

.auth-submit-btn:active {
  transform: translateY(0);
}

.auth-card-footer {
  text-align: center;
  font-size: 14px;
  color: var(--text-tertiary);
}

.auth-card-footer a {
  color: var(--accent-info);
  text-decoration: none;
  margin-left: 4px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s ease;
}

.auth-card-footer a:hover {
  color: var(--accent-primary);
  text-decoration: underline;
}

/* 输入框样式覆盖 */
.auth-form :deep(.el-input__wrapper) {
  padding: 13px 16px !important;
  background: var(--glass-bg) !important;
  backdrop-filter: blur(var(--blur-light)) !important;
  -webkit-backdrop-filter: blur(var(--blur-light)) !important;
  border: 1px solid var(--glass-border) !important;
  border-radius: var(--radius-md) !important;
  box-shadow: none !important;
}

.auth-form :deep(.el-input__wrapper:hover),
.auth-form :deep(.el-input__wrapper:focus-within) {
  background: var(--glass-bg-hover) !important;
  border-color: var(--glass-border-hover) !important;
}

.auth-form :deep(.el-input__inner) {
  font-size: 15px;
  color: var(--text-primary) !important;
}

.auth-form :deep(.el-input__inner::placeholder) {
  color: var(--text-tertiary) !important;
}

.auth-form :deep(.el-input__prefix) {
  color: var(--text-secondary) !important;
}
</style>
