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
  },
  build: {
    rollupOptions: {
      output: {
        /*
          把第三方库切成三块。目的不是"消灭 500 kB 警告"，是**缓存粒度**：
          业务代码几乎每周都变，而这几个 vendor 几个月不动。混在一个 chunk 里，
          用户每次发版都要重下整包；拆开之后只有变的那块失效。

          页面级的拆分不在这里 —— 那是 router 的 `lazy` 做的（见 router/index.tsx）。

          注意别把 @base-ui/react 拆得太碎：它内部有大量互相引用的小模块，
          切成多块反而会产生循环依赖警告。
        */
        manualChunks: {
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          'vendor-ui': [
            '@base-ui/react',
            'sonner',
            'lucide-react',
            'class-variance-authority',
            'cn'
          ],
          'vendor-form': ['react-hook-form', '@hookform/resolvers', 'zod']
        }
      }
    }
  }
})
