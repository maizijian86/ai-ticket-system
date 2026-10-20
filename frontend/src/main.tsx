import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

/*
  只管挂载。`<html>` 的 class 由 index.html 给（全站白底，不挂 .dark）。

  ── StrictMode 是 2026-10 打开的（Phase 6）──

  此前一直没开，而且理由换过两轮：

    1. 最早是为了 MicroSlats 那片自管 WebGL2 上下文的海 —— 双跑 effect 会多创建
       一次 context。这两个组件（海、首页那条 ScrollVelocity）都已删除。
    2. 后来"没开只是因为没业务理由去动它"。那也不算理由。

  开之前核过那条唯一的硬约束：**命令式 AI 调用绝不能放进 effect**（双跑下就是
  两份 10 分钟的请求）。现在三个 AI 动作 —— 分析、对话、下载 PRD —— 全部在
  `useTicketAi` 的 useCallback 里，由事件处理器触发；effect 里只有 `reloadQuota()`
  这种只读 GET 与状态复位，重跑无副作用。所以可以开。

  注意 StrictMode **不会**让生产构建双跑，它只在开发期暴露问题。真出问题说明
  effect 本身不干净（缺少清理、或在 effect 里做了本该由事件触发的事），
  该改的是代码，不是把它关掉。
*/

const container = document.getElementById('root')
if (!container) {
  throw new Error('找不到 #root 挂载点，检查 index.html')
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>
)
