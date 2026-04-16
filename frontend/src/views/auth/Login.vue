<template>
  <div class="login-container">
    <!-- 装饰性背景元素 -->
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>
    <div class="bg-orb bg-orb-3"></div>

    <div class="login-card glass-card">
      <div class="login-header">
        <div class="logo-icon">
          <el-icon size="36"><Ticket /></el-icon>
        </div>
        <h1>AI 智能工单系统</h1>
        <p class="subtitle">智能分类 / 自动派单 / RAG 回复</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="login-form glass-form-item" @submit.prevent="handleLogin">
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
            class="login-btn"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span>还没有账号？</span>
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores'
import { User, Lock, Ticket } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
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
        await authStore.login(form.username, form.password)
        router.push('/tickets')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  position: relative;
  overflow: hidden;
}

/* 装饰性背景光球 */
.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  animation: float 8s ease-in-out infinite;
}

.bg-orb-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, var(--accent-primary) 0%, transparent 70%);
  top: -100px;
  right: -100px;
  animation-delay: 0s;
}

.bg-orb-2 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, var(--accent-tertiary) 0%, transparent 70%);
  bottom: -50px;
  left: -50px;
  animation-delay: -2s;
}

.bg-orb-3 {
  width: 250px;
  height: 250px;
  background: radial-gradient(circle, var(--accent-secondary) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -4s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.05); }
  66% { transform: translate(-20px, 20px) scale(0.95); }
}

/* 登录卡片 */
.login-card {
  width: 400px;
  padding: 48px 40px;
  position: relative;
  z-index: 10;
  animation: slideUp 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
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

.login-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
  letter-spacing: -0.5px;
}

.subtitle {
  font-size: 14px;
  color: var(--text-tertiary);
  letter-spacing: 2px;
}

.login-form {
  margin-bottom: 32px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-btn {
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

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(0, 122, 255, 0.5);
}

.login-btn:active {
  transform: translateY(0);
}

.login-btn:disabled {
  opacity: 0.7;
  transform: none;
}

.login-footer {
  text-align: center;
  font-size: 14px;
  color: var(--text-tertiary);
}

.login-footer a {
  color: var(--accent-info);
  text-decoration: none;
  margin-left: 4px;
  font-weight: 500;
  transition: color 0.2s ease;
}

.login-footer a:hover {
  color: var(--accent-primary);
  text-decoration: underline;
}

/* 输入框样式覆盖 */
.login-form :deep(.el-input__wrapper) {
  padding: 14px 16px !important;
  background: var(--glass-bg) !important;
  backdrop-filter: blur(var(--blur-light)) !important;
  -webkit-backdrop-filter: blur(var(--blur-light)) !important;
  border: 1px solid var(--glass-border) !important;
  border-radius: var(--radius-md) !important;
  box-shadow: none !important;
}

.login-form :deep(.el-input__wrapper:hover),
.login-form :deep(.el-input__wrapper:focus-within) {
  background: var(--glass-bg-hover) !important;
  border-color: var(--glass-border-hover) !important;
}

.login-form :deep(.el-input__inner) {
  font-size: 15px;
  color: var(--text-primary) !important;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: var(--text-tertiary) !important;
}

.login-form :deep(.el-input__prefix) {
  color: var(--text-secondary) !important;
}
</style>
