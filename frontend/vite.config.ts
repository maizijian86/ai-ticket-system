import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig({
  // Vue 插件已摘除：迁移收尾时把 .vue 全部删干净了（见 改动计划 的 Phase 2）。
  // 现在这套是纯 React 技术栈：
  //   react    —— .tsx / .jsx
  //   tailwind —— Tailwind v4（peer 是 ^5.2.0 || ^6 || ^7 || ^8，vite 5.4 在射程内）
  // 注意 @vitejs/plugin-react 要停在 4.x：5.0 起要求 vite@8，本项目是 vite@5。
  plugins: [react(), tailwindcss()],
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
