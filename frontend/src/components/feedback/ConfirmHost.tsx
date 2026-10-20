import { useEffect, useRef, useState } from 'react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from '@/components/ui/alert-dialog'

/**
 * 全站唯一的确认框宿主，配一个命令式的 `confirmDialog()`。
 *
 * 为什么要命令式：旧代码用 `ElMessageBox.confirm(...)` 的地方是 6 个**事件处理器**，
 * 写法是 `await ElMessageBox.confirm('确定删除？')` 然后接着做事。改成声明式的
 * `<AlertDialog open={...}>` 就得在每个调用点各加一个 state、各渲染一份对话框、
 * 把"确定之后要干什么"从 await 后面挪进回调里 —— 6 处都要改结构，还容易漏掉
 * "关掉对话框时把 loading 复位"这类收尾。
 *
 * 这样写还有个附带好处：**对话框永远只有一份**。声明式写法很容易在列表里给每行
 * 都渲染一个对话框，20 行就是 20 个 DOM 节点。
 */
export type ConfirmOptions = {
  title: string
  description?: string
  confirmText?: string
  cancelText?: string
  /** 删除、驳回这类不可逆操作传 true —— 确认键变销毁色。 */
  danger?: boolean
}

type Pending = { opts: ConfirmOptions; resolve: (ok: boolean) => void }

/**
 * 由 ConfirmHost 在挂载时填上。放在模块作用域而不是 Context，是因为调用点
 * 都在事件处理器里（而非组件内），拿不到 Context。
 */
let present: ((opts: ConfirmOptions) => Promise<boolean>) | null = null

/**
 * 弹一个确认框，返回用户是否点了确定。
 *
 * 简写：`confirmDialog('确定删除这张工单？')` 等价于只传 title。
 */
export function confirmDialog(options: ConfirmOptions | string): Promise<boolean> {
  const opts = typeof options === 'string' ? { title: options } : options
  if (!present) {
    // 退化而不是抛错：漏挂宿主不该让一次删除静默发生。
    console.warn('[ConfirmHost] 未挂载，确认框按"取消"处理')
    return Promise.resolve(false)
  }
  return present(opts)
}

export function ConfirmHost() {
  const [pending, setPending] = useState<Pending | null>(null)
  // 关闭路径有两条（点取消/ESC 走 onOpenChange，点确定走 onClick），
  // 用 ref 守住 promise 只被 settle 一次。
  const settled = useRef(true)

  useEffect(() => {
    present = (opts) =>
      new Promise<boolean>((resolve) => {
        settled.current = false
        setPending({ opts, resolve })
      })
    return () => {
      present = null
    }
  }, [])

  function settle(ok: boolean) {
    if (settled.current || !pending) return
    settled.current = true
    pending.resolve(ok)
    setPending(null)
  }

  return (
    <AlertDialog open={!!pending} onOpenChange={(open) => !open && settle(false)}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{pending?.opts.title}</AlertDialogTitle>
          {pending?.opts.description && (
            <AlertDialogDescription>{pending.opts.description}</AlertDialogDescription>
          )}
        </AlertDialogHeader>
        {/*
          AlertDialogAction 在这个 registry 里**只是按钮、不是关闭器**（不像
          AlertDialogCancel 包了 Close），所以"确定"必须自己调 settle —— 忘了就会
          点了没反应，对话框赖在屏幕上。
        */}
        <AlertDialogFooter>
          <AlertDialogCancel>{pending?.opts.cancelText ?? '取消'}</AlertDialogCancel>
          <AlertDialogAction
            variant={pending?.opts.danger ? 'destructive' : 'default'}
            onClick={() => settle(true)}
          >
            {pending?.opts.confirmText ?? '确定'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
