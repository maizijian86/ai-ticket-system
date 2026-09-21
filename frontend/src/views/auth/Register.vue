<template>
  <div class="register-container">
    <!-- 装饰性背景元素 -->
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>
    <div class="bg-orb bg-orb-3"></div>

    <div class="register-card glass-card">
      <div class="register-header">
        <div class="logo-icon">
          <el-icon size="36"><Ticket /></el-icon>
        </div>
        <h1>注册账号</h1>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="register-form glass-form-item" @submit.prevent="handleRegister">
        <el-form-item prop="username" class="glass-input">
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="email" class="glass-input">
          <el-input v-model="form.email" placeholder="邮箱" size="large" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item prop="nickname" class="glass-input">
          <el-input v-model="form.nickname" placeholder="昵称（可选）" size="large" :prefix-icon="UserFilled" />
        </el-form-item>
        <el-form-item prop="password" class="glass-input">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword" class="glass-input">
          <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" size="large" :prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="register-btn" @click="handleRegister">
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-footer">
        <span>已有账号？</span>
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores'
import { User, Lock, Message, UserFilled, Ticket } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule: any, value: any, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

async function handleRegister() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await authStore.register({
          username: form.username,
          email: form.email,
          nickname: form.nickname || undefined,
          password: form.password
        })
        ElMessage.success('注册成功，请登录')
        router.push('/login')
      } catch {
        // error handled by interceptor
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.register-container {
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
  background: radial-gradient(circle, var(--accent-secondary) 0%, transparent 70%);
  top: -100px;
  left: -100px;
  animation-delay: -1s;
}

.bg-orb-2 {
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, var(--accent-primary) 0%, transparent 70%);
  bottom: -50px;
  right: -50px;
  animation-delay: -3s;
}

.bg-orb-3 {
  width: 250px;
  height: 250px;
  background: radial-gradient(circle, var(--accent-tertiary) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -5s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-30px, 30px) scale(1.05); }
  66% { transform: translate(20px, -20px) scale(0.95); }
}

/* 注册卡片 */
.register-card {
  width: 420px;
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

.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  background: linear-gradient(135deg, var(--accent-secondary), var(--accent-tertiary), var(--accent-primary));
  background-size: 200% 200%;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 8px 32px rgba(94, 92, 230, 0.4);
  animation: gradientShift 4s ease infinite;
}

@keyframes gradientShift {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

.register-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.register-form {
  margin-bottom: 24px;
}

.register-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.register-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--accent-secondary), var(--accent-tertiary)) !important;
  border: none !important;
  color: #fff;
  box-shadow: 0 4px 20px rgba(94, 92, 230, 0.4);
  transition: all 0.3s ease;
}

.register-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(94, 92, 230, 0.5);
}

.register-btn:active {
  transform: translateY(0);
}

.register-btn:disabled {
  opacity: 0.7;
  transform: none;
}

.register-footer {
  text-align: center;
  font-size: 14px;
  color: var(--text-tertiary);
}

.register-footer a {
  color: var(--accent-info);
  text-decoration: none;
  margin-left: 4px;
  font-weight: 500;
  transition: color 0.2s ease;
}

.register-footer a:hover {
  color: var(--accent-secondary);
  text-decoration: underline;
}

/* 输入框样式覆盖 */
.register-form :deep(.el-input__wrapper) {
  padding: 14px 16px !important;
  background: var(--glass-bg) !important;
  backdrop-filter: blur(var(--blur-light)) !important;
  -webkit-backdrop-filter: blur(var(--blur-light)) !important;
  border: 1px solid var(--glass-border) !important;
  border-radius: var(--radius-md) !important;
  box-shadow: none !important;
}

.register-form :deep(.el-input__wrapper:hover),
.register-form :deep(.el-input__wrapper:focus-within) {
  background: var(--glass-bg-hover) !important;
  border-color: var(--glass-border-hover) !important;
}

.register-form :deep(.el-input__inner) {
  font-size: 15px;
  color: var(--text-primary) !important;
}

.register-form :deep(.el-input__inner::placeholder) {
  color: var(--text-tertiary) !important;
}

.register-form :deep(.el-input__prefix) {
  color: var(--text-secondary) !important;
}
</style>
