import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

/*
  只管挂载。`<html>` 的 class 由 index.html 给（现在连 .dark 都不挂了 —— 全站白底）。

  **没有包 StrictMode**，当前是有意为之 —— 但理由已经换过一轮了，别照着旧注释理解。

  原来的理由（MicroSlats 那片自管 WebGL2 上下文的海做全站背景，双跑 effect 会
  多创建一次 context）**已经不成立**：海和首页那条 ScrollVelocity 都撤掉了
  （见 index.css 顶部与 pages/Home.tsx 末尾）。站里现在没有任何自管 WebGL /
  手动 rAF 的上游组件，这条理由连一个例子都举不出来了。

  也就是说：现在开着 StrictMode 大概率是好事（P5 那两个巨页的 effect 最需要用
  双跑来揪清理漏洞）。没开只是因为没有业务理由去动它，不是因为有什么挡着。
  真要开，顺手把「命令式 AI 调用绝不放进 effect」这条再核一遍 —— 双跑下那就是
  两份 10 分钟请求。

  不包 StrictMode **不等于**可以写脏 effect：数据加载照旧带 cleanup（中止请求、
  置 ignore 标志），命令式的 AI 调用照旧只放在事件处理器里、绝不放进 effect ——
  这些是为了正确性，跟有没有 StrictMode 无关。*/

const container = document.getElementById('root')
if (!container) {
  throw new Error('找不到 #root 挂载点，检查 index.html')
}

createRoot(container).render(<App />)
