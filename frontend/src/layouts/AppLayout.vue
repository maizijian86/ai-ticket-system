<template>
  <el-container class="app-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapsed ? '72px' : '240px'" class="sidebar">
      <!-- Logo 区域 -->
      <div class="logo glass-card-static">
        <div class="logo-icon">
          <el-icon size="22"><Ticket /></el-icon>
        </div>
        <span v-show="!isCollapsed" class="logo-text">AI 工单</span>
      </div>

      <!-- 导航菜单 -->
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        class="sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/tickets" class="menu-item">
          <el-icon><List /></el-icon>
          <template #title>工单列表</template>
        </el-menu-item>
        <el-menu-item index="/my-tickets" class="menu-item">
          <el-icon><User /></el-icon>
          <template #title>我的工单</template>
        </el-menu-item>
        <el-menu-item index="/pending" class="menu-item">
          <el-icon><Clock /></el-icon>
          <template #title>待处理</template>
        </el-menu-item>
        <el-menu-item index="/knowledge" class="menu-item">
          <el-icon><Reading /></el-icon>
          <template #title>知识库</template>
        </el-menu-item>
        <el-sub-menu index="admin" v-if="authStore.isAdmin" class="menu-item">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>管理</span>
          </template>
          <el-menu-item index="/admin/users">用户管理</el-menu-item>
          <el-menu-item index="/admin/stats">统计分析</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container class="main-container">
      <!-- 顶部栏 -->
      <el-header class="header glass-card-static">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapsed = !isCollapsed">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
        </div>
        <div class="header-right">
          <!-- 未登录显示登录按钮 -->
          <el-button v-if="!authStore.token" @click="router.push('/login')" class="btn-glass btn-glass-primary">
            登录
          </el-button>
          <!-- 已登录显示用户信息 -->
          <el-dropdown v-else @command="handleCommand" trigger="click" class="glass-dropdown">
            <span class="user-info">
              <div class="user-avatar">
                <el-icon><UserFilled /></el-icon>
              </div>
              <span class="user-name">{{ authStore.userInfo?.nickname || authStore.userInfo?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main-content">
        <router-view :key="route.fullPath" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores'
import {
  Ticket, List, User, Clock, Reading, Setting,
  Fold, Expand, UserFilled, ArrowDown
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isCollapsed = ref(false)

const activeMenu = computed(() => route.path)

function handleMenuSelect(index: string) {
  router.push(index)
}

function handleCommand(command: string) {
  if (command === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.app-layout {
  height: 100vh;
  display: flex;
}

/* ========================================
   侧边栏
   ======================================== */
.sidebar {
  background: rgba(255, 255, 255, 0.03);
  backdrop-filter: blur(var(--blur-medium));
  -webkit-backdrop-filter: blur(var(--blur-medium));
  border-right: 1px solid var(--glass-border);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 0 20px;
  margin: 16px;
  border-radius: var(--radius-lg) !important;
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-tertiary));
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 4px 16px rgba(0, 122, 255, 0.4);
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #fff 0%, rgba(255,255,255,0.7) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.sidebar-menu {
  flex: 1;
  background: transparent;
  border-right: none;
  padding: 8px;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  border-radius: var(--radius-md);
  margin-bottom: 4px;
  color: var(--text-secondary);
  transition: all 0.3s ease;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: var(--glass-bg-hover);
  color: var(--text-primary);
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(0, 122, 255, 0.3), rgba(94, 92, 230, 0.3));
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.sidebar-menu :deep(.el-sub-menu__title) {
  height: 48px;
  line-height: 48px;
  border-radius: var(--radius-md);
  margin-bottom: 4px;
  color: var(--text-secondary);
}

.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background: var(--glass-bg-hover);
  color: var(--text-primary);
}

/* ========================================
   顶部栏
   ======================================== */
.header {
  height: 72px !important;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px !important;
  margin: 16px;
  margin-bottom: 0;
  border-radius: var(--radius-lg) !important;
}

.header-left {
  display: flex;
  align-items: center;
}

.collapse-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--text-secondary);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.3s ease;
}

.collapse-btn:hover {
  background: var(--glass-bg-hover);
  color: var(--text-primary);
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.3s ease;
}

.user-info:hover {
  background: var(--glass-bg-hover);
}

.user-avatar {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, var(--accent-secondary), var(--accent-tertiary));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

/* ========================================
   主内容区
   ======================================== */
.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.main-content {
  padding: 24px;
  overflow-y: auto;
  height: calc(100vh - 88px);
}
</style>
