/**
 * 施工中占位页。
 *
 * 迁移策略是「先立 React 壳、未迁移的路由渲染占位」，而不是在 Vue 壳里继续嵌
 * React 页面 —— 后者要给 20 个页面各写一层临时的路由桥，最后翻壳时还得返工第二遍。
 *
 * 所以这个组件是**临时**的，P5 结束时会连同最后一个占位路由一起消失。刻意把它写得
 * 很简单：任何聪明之处都是在给删除增加阻力。
 *
 * 显示当前路径是必要的 —— 测试导航时（尤其是守卫把用户弹到哪去了）必须一眼看出
 * 此刻落在哪个路由上，否则「点了个受保护的链接」和「什么都没发生」长得一样。
 */
export default function Placeholder({ title }: { title: string }) {
  return (
    <div className="mx-auto w-full max-w-3xl px-6 py-24">
      <p className="text-xs font-medium tracking-[0.2em] text-muted-foreground uppercase">
        Under construction
      </p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight">{title}</h1>
      <p className="mt-4 max-w-prose text-sm leading-6 text-muted-foreground">
        这个页面还在从 Vue 迁到 React 的路上。导航、登录态与路由守卫已经是真的了 ——
        可以从这里点走、退出、再被拦回来试试。
      </p>
    </div>
  )
}
