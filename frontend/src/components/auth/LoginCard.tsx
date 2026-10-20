import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Gift, LoaderCircle, Lock, User } from '@/components/icons'
import { UserAvatar } from '@/components/user/UserAvatar'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/authStore'
import { useUiStore } from '@/stores/uiStore'
import { SIGNUP_PROMO_TITLE } from '@/utils/ai-quota'

const schema = z.object({
  username: z.string().min(1, '请输入用户名'),
  password: z.string().min(1, '请输入密码')
})

type FormValues = z.infer<typeof schema>

export default function LoginCard() {
  const login = useAuthStore((s) => s.login)
  const switchAuthCard = useUiStore((s) => s.switchAuthCard)
  const [loading, setLoading] = useState(false)

  const {
    register,
    handleSubmit,
    control,
    formState: { errors }
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: '', password: '' }
  })

  /**
   * 边输边显示头像。
   *
   * 300ms 防抖：按 `admin2` 会打出 6 个中间前缀，每个都发一次请求既浪费又会让
   * 头像在几种状态之间闪。防抖窗口内的最后一次输入才发请求，`alive` 保证
   * 旧请求不会在用户已经改完用户名之后才写回。
   *
   * 查询失败或用户不存在都只是"没有头像"，不能在这里 toast —— 输到一半的
   * `adm` 查不到是完全正常的。
   */
  const username = useWatch({ control, name: 'username' }) ?? ''
  const [avatarUrl, setAvatarUrl] = useState<string | undefined>(undefined)

  useEffect(() => {
    const name = username.trim()
    if (!name) {
      setAvatarUrl(undefined)
      return
    }

    let alive = true
    const timer = window.setTimeout(() => {
      userApi
        .getAvatarByUsername(name)
        .then((res) => {
          if (alive) setAvatarUrl(res?.avatarUrl ?? undefined)
        })
        .catch(() => {
          if (alive) setAvatarUrl(undefined)
        })
    }, 300)

    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [username])

  async function onSubmit(values: FormValues) {
    setLoading(true)
    try {
      // 登录成功后由 store 统一关掉卡片，这里不再管浮层的显隐。
      // 也不 jump 到 /tickets：卡片可能是在任意页面被打开的，强行跳走会把用户
      // 从他正在看的内容上拽开（守卫已经把未登录用户送到大厅了，那里就是最合适的位置）。
      await login(values.username, values.password)
    } catch {
      // 错误提示由 axios 拦截器统一弹出，这里吞掉即可
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="w-[400px] max-w-full overflow-y-auto rounded-xl border border-border bg-card p-9 shadow-2xl"
      style={{ animation: 'auth-card-in 0.35s cubic-bezier(0.4,0,0.2,1)' }}
    >
      <header className="mb-5 text-center">
        <div className="mx-auto mb-3.5 flex size-15 items-center justify-center rounded-xl bg-foreground text-background shadow-lg shadow-foreground/20">
          {/* 官方 LOGO 的白色版本（黑底块上只能用反白的那份） */}
          <img src="/logo-white.png" alt="" className="h-8 w-auto" />
        </div>
        {/* 品牌名跟着 LOGO 走：官方名字是「百川灵构」，不再是旧的产品名 */}
        <h1 className="text-xl font-bold tracking-tight">百川灵构</h1>
        <p className="mt-1.5 text-[13px] tracking-[0.1em] text-muted-foreground">
          智能分类 / 自动派单 / RAG 回复
        </p>
      </header>

      {/*
        活动位。登录卡片是"未登录用户停留时间最长的一块界面"，把赠送额度说在这里
        比只放在大厅横幅上更有可能被看到。
      */}
      <div className="mb-5 flex items-center justify-center gap-1.5 rounded-md border border-amber-300 bg-amber-50 px-3 py-2.5 text-[13px] font-medium text-amber-700">
        <Gift size={14} />
        <span>{SIGNUP_PROMO_TITLE}</span>
      </div>

      {/* noValidate：校验交给 zod，别让浏览器用系统语言的原生气泡抢先插一嘴 */}
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="mb-5 space-y-3.5">
        <div>
          <Label htmlFor="login-username" className="sr-only">
            用户名
          </Label>
          <div className="relative">
            {/*
              查到人就用他的头像替掉左侧的 User 图标；查不到（或还没输完）
              仍然是图标 —— 输入框的左边距随之切换，避免头像压在文字上。
            */}
            {avatarUrl ? (
              <UserAvatar
                avatarUrl={avatarUrl}
                name={username}
                className="pointer-events-none absolute top-1/2 left-2 size-8 -translate-y-1/2"
              />
            ) : (
              <User
                size={16}
                className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground"
              />
            )}
            <Input
              id="login-username"
              placeholder="用户名"
              autoComplete="username"
              disabled={loading}
              className={avatarUrl ? 'h-12 pl-12 text-[15px]' : 'h-12 pl-9 text-[15px]'}
              {...register('username')}
            />
          </div>
          {errors.username && (
            <p className="mt-1.5 text-xs text-destructive">{errors.username.message}</p>
          )}
        </div>

        <div>
          <Label htmlFor="login-password" className="sr-only">
            密码
          </Label>
          <div className="relative">
            <Lock
              size={16}
              className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground"
            />
            <Input
              id="login-password"
              type="password"
              placeholder="密码"
              autoComplete="current-password"
              disabled={loading}
              className="h-12 pl-9 text-[15px]"
              {...register('password')}
            />
          </div>
          {errors.password && (
            <p className="mt-1.5 text-xs text-destructive">{errors.password.message}</p>
          )}
        </div>

        <Button type="submit" disabled={loading} className="h-12 w-full text-base font-semibold">
          {loading && <LoaderCircle className="size-4 animate-spin" />}
          登录
        </Button>
      </form>

      <div className="text-center text-sm text-muted-foreground">
        <span>还没有账号？</span>
        {/* 卡片内部互相切换，不跳路由 —— /login、/register 两个整页路由已删除 */}
        <button
          type="button"
          onClick={() => switchAuthCard('register')}
          className="ml-1 cursor-pointer font-medium text-primary hover:underline"
        >
          立即注册
        </button>
      </div>
    </div>
  )
}
