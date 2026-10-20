import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig({
  // 正在从 Vue 重写成 React（分支 feat/react-rewrite，见计划文件）。
  // 过渡期三个插件并存：
  //   vue   —— 管还没迁走的 .vue
  //   react —— 管 .jsx/.tsx
  //   tailwind —— Tailwind v4（peer 是 ^5.2.0 || ^6 || ^7 || ^8，vite 5.4 在射程内）
  // Vue 插件在全部页面迁完后摘掉。
  // 注意 @vitejs/plugin-react 要停在 4.x：5.0 起要求 vite@8，本项目是 vite@5。
  plugins: [vue(), react(), tailwindcss()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        // 用 127.0.0.1 而不是 localhost：网关的 server.address 已绑回环（仅 IPv4），
        // 而 Node 17+ 的 dns-result-order 默认是 verbatim，localhost 在 Windows 上
        // 常先解析到 ::1 —— 那一跳会 ECONNREFUSED。
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
