import { Toaster as Sonner, type ToasterProps } from "sonner"
import { CircleCheckIcon, InfoIcon, TriangleAlertIcon, OctagonXIcon, Loader2Icon } from "lucide-react"

/**
 * 主题**写死 light**，没有走 shadcn 原版的 `useTheme()`。
 *
 * 原版是从 next-themes 读当前主题的，而本项目根本没有主题切换 —— 全站就是白底
 * 黑字（index.html 的 <html> 上连 .dark 都不挂）。没有 ThemeProvider 时
 * `useTheme()` 返回的是 "system"，也就是 toast 会跟着**操作系统**走：系统是深色的
 * 用户会看到一条黑底提示条压在白纸上。写死比引一个 Provider 只为读一个常量诚实。
 *
 * 字面量一旦和 index.css 的 :root 漂移，症状很隐蔽 —— sonner 只在 "system" 分支
 * 才去问系统，写死之后它连问都不问，配色完全由这个字面量说了算。
 */
const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      theme="light"
      className="toaster group"
      icons={{
        success: <CircleCheckIcon className="size-4" />,
        info: <InfoIcon className="size-4" />,
        warning: <TriangleAlertIcon className="size-4" />,
        error: <OctagonXIcon className="size-4" />,
        loading: <Loader2Icon className="size-4 animate-spin" />
      }}
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--border-radius": "var(--radius)"
        } as React.CSSProperties
      }
      toastOptions={{
        classNames: {
          toast: "cn-toast"
        }
      }}
      {...props}
    />
  )
}

export { Toaster }
