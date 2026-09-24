import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
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
