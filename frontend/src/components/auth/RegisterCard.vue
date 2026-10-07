<template>
  <div class="auth-card register-card glass-card">
    <div class="auth-card-header">
      <div class="logo-icon">
        <el-icon size="30"><Ticket /></el-icon>
      </div>
      <h1>注册账号</h1>
      <p class="subtitle">{{ SIGNUP_PROMO_TITLE }}</p>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" class="auth-form glass-form-item" @submit.prevent="handleRegister">
      <!--
        身份选择。放在所有输入框之前，因为它是这张表单上唯一一个"选错要重注册"
        的字段 —— 用户名错了可以改，身份注册完只能找管理员改。
      -->
      <el-form-item prop="role" class="role-form-item">
        <div class="role-picker">
          <div
            v-for="opt in ROLE_OPTIONS"
            :key="opt.value"
            class="role-card"
            :class="{ 'role-card-active': form.role === opt.value }"
            @click="pickRole(opt.value)"
          >
            <div v-if="form.role === opt.value" class="role-card-check">
              <el-icon size="12"><Check /></el-icon>
            </div>
            <div class="role-card-icon">
              <el-icon size="24"><component :is="opt.icon" /></el-icon>
            </div>
            <div class="role-card-title">{{ opt.title }}</div>
            <div class="role-card-desc">{{ opt.desc }}</div>
          </div>
        </div>
      </el-form-item>

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
        <el-button type="primary" size="large" :loading="loading" class="auth-submit-btn register-submit-btn" @click="handleRegister">
          注册
        </el-button>
      </el-form-item>
    </el-form>

    <div class="auth-card-footer">
      <span>已有账号？</span>
      <a href="javascript:void(0)" @click="authStore.switchAuthCard('login')">立即登录</a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores'
import { User, Lock, Message, UserFilled, Ticket, Check, Promotion, Suitcase } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { SIGNUP_PROMO_TITLE, SIGNUP_BONUS_CREDITS } from '@/utils/ai-quota'
import type { FormInstance, FormRules } from 'element-plus'
import type { RegisterRole } from '@/types'

const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

/**
 * 两种身份，与后端 Role 的 PUBLISHER / HANDLER 对应。
 *
 * 之前身份是隐式的 —— 注册页根本没有这个字段，所有新账号都落到后端的默认值
 * USER（"发布和接单都能做"的存量身份）。结果是"身份"这个维度在前端从来没有
 * 被表达过，而大厅的卡片正要按它分叉。
 */
const ROLE_OPTIONS: { value: RegisterRole; title: string; desc: string; icon: any }[] = [
  {
    value: 'PUBLISHER',
    title: '我要发布需求',
    desc: '我有项目要做，想找人帮我开发',
    icon: Promotion
  },
  {
    value: 'HANDLER',
    title: '我要接单开发',
    desc: '我能写代码，想接单做项目',
    icon: Suitcase
  }
]

const form = reactive({
  role: '' as RegisterRole | '',
  username: '',
  email: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

function pickRole(value: RegisterRole) {
  form.role = value
  // 立刻清掉红字：这里没有 el-input 参与，el-form 不会自己触发 change 校验，
  // 用户选完了还留着"请选择你的身份"会很怪。
  formRef.value?.clearValidate('role')
}

const validateConfirmPassword = (_rule: any, value: any, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  role: [{ required: true, message: '请先选择你的身份', trigger: 'change' }],
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
      // 上面的 required 规则已经拦过空值了，这里再判一次只是为了让 TS 把
      // RegisterRole | '' 收窄成 RegisterRole —— 比写 as 断言诚实。
      if (!form.role) return
      loading.value = true
      try {
        await authStore.register({
          username: form.username,
          email: form.email,
          nickname: form.nickname || undefined,
          password: form.password,
          role: form.role
        })
        ElMessage.success(`注册成功，已赠送 ${SIGNUP_BONUS_CREDITS} 次 AI 辅助开发额度，请登录`)
        // 不再跳 /login（整页登录路由已删）—— 就地切回登录态，用户少一次跳转，
        // 用户名也还在输入框里（这里刻意清空密码，避免用户以为已经登录了）。
        form.password = ''
        form.confirmPassword = ''
        formRef.value?.clearValidate()
        authStore.switchAuthCard('login')
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
/*
 * 样式来自原 views/auth/Register.vue，去掉的是"整页"那一层：100vh 容器与三个
 * 全屏背景光球。身份选择器、渐变按钮、输入框覆盖全部原样保留。
 */
.auth-card {
  width: 520px;
  max-width: 100%;
  /* 注册卡比登录卡高得多（身份选择 + 5 个输入框）。窗口不够高时让卡片**内部**
     滚动，而不是让整页滚走卡片上半部分 —— 浮层是 fixed 的，一旦超出视口，
     用户就再也够不到顶部的身份选择器了。 */
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  padding: 32px 32px 28px;
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

.auth-card-header h1 {
  font-size: 21px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
  letter-spacing: -0.5px;
}

.subtitle {
  font-size: 13px;
  font-weight: 500;
  color: var(--accent-warning);
}

/* ---------------- 身份选择 ---------------- */
.role-form-item {
  margin-bottom: 18px;
}

/* el-form-item 的 content 默认是 flex 且子项不铺满，这里让它占满整行 */
.role-form-item :deep(.el-form-item__content) {
  width: 100%;
}

.role-picker {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  width: 100%;
}

.role-card {
  position: relative;
  padding: 16px 12px 14px;
  border-radius: var(--radius-md);
  border: 1.5px solid var(--glass-border);
  background: var(--glass-bg);
  cursor: pointer;
  text-align: center;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.role-card:hover {
  border-color: var(--glass-border-hover);
  background: var(--glass-bg-hover);
  transform: translateY(-2px);
}

.role-card-active {
  border-color: var(--accent-secondary) !important;
  background: var(--glass-bg-hover) !important;
  box-shadow: 0 0 0 3px rgba(94, 92, 230, 0.18), 0 8px 24px rgba(94, 92, 230, 0.22);
}

.role-card-check {
  position: absolute;
  top: 0;
  right: 0;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--accent-secondary), var(--accent-tertiary));
  border-radius: 0 var(--radius-md) 0 var(--radius-md);
}

.role-card-icon {
  width: 46px;
  height: 46px;
  margin: 0 auto 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-info));
  box-shadow: 0 4px 16px rgba(0, 122, 255, 0.35);
  transition: transform 0.25s ease;
}

/* 第二张（接单开发）用另一套色，两张卡一眼能分开 */
.role-card:nth-child(2) .role-card-icon {
  background: linear-gradient(135deg, var(--accent-secondary), var(--accent-tertiary));
  box-shadow: 0 4px 16px rgba(94, 92, 230, 0.35);
}

.role-card-active .role-card-icon {
  transform: scale(1.08);
}

.role-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 5px;
}

.role-card-desc {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-tertiary);
}

.auth-form {
  margin-bottom: 18px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 15px;
}

.auth-submit-btn {
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

.auth-submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(94, 92, 230, 0.5);
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
  color: var(--accent-secondary);
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
