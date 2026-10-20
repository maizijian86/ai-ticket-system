import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Briefcase, Check, LoaderCircle, Lock, Mail, Send, User, UserRound } from '@/components/icons'
import { UserAvatar } from '@/components/user/UserAvatar'
import { useAuthStore } from '@/stores/authStore'
import { useUiStore } from '@/stores/uiStore'
import { SIGNUP_BONUS_CREDITS, SIGNUP_PROMO_TITLE } from '@/utils/ai-quota'
import type { RegisterRole } from '@/types'

/**
 * 两种身份，与后端 Role 的 PUBLISHER / HANDLER 对应。
 *
 * 之前身份是隐式的 —— 注册页根本没有这个字段，所有新账号都落到后端的默认值
 * USER（"发布和接单都能做"的存量身份）。结果是"身份"这个维度在前端从来没有
 * 被表达过，而大厅的卡片正要按它分叉。
 */
const ROLE_OPTIONS = [
  { value: 'PUBLISHER', title: '我要发布需求', desc: '我有项目要做，想找人帮我开发', Icon: Send },
  { value: 'HANDLER', title: '我要接单开发', desc: '我能写代码，想接单做项目', Icon: Briefcase }
] as const

const ROLE_VALUES: readonly string[] = ROLE_OPTIONS.map((o) => o.value)

/**
 * role 在表单里是 string（身份选择器不是原生 input，收不到 zod 的枚举约束那么好用），
 * 提交前用它收窄成 RegisterRole。
 *
 * 用一个真的判断而不是 `as RegisterRole`：断言会在后端加了第三种角色、而这里没跟上时
 * 一声不吭地把脏值发出去；这个判断至少把"没选"和"选了非法值"挡在同一处。
 */
function isRegisterRole(v: string): v is RegisterRole {
  return ROLE_VALUES.includes(v)
}

const schema = z
  .object({
    role: z.string().min(1, '请先选择你的身份'),
    username: z
      .string()
      .min(1, '请输入用户名')
      .min(3, '用户名长度 3-50 个字符')
      .max(50, '用户名长度 3-50 个字符'),
    email: z.string().min(1, '请输入邮箱').email('请输入正确的邮箱格式'),
    nickname: z.string(),
    password: z.string().min(1, '请输入密码').min(6, '密码至少 6 个字符'),
    confirmPassword: z.string().min(1, '请确认密码')
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: '两次输入的密码不一致',
    path: ['confirmPassword']
  })

type FormValues = z.infer<typeof schema>

export default function RegisterCard() {
  const registerUser = useAuthStore((s) => s.register)
  const switchAuthCard = useUiStore((s) => s.switchAuthCard)
  const [loading, setLoading] = useState(false)
  /**
   * 注册成功后的凭据。有值就把整张卡换成"成功态"（大号头像 + 用户名 + 去登录），
   * 而不是立刻切回登录卡 —— 头像是我方随机分配的，用户在这一刻不看就再也没
   * 机会知道自己长什么样了。
   */
  const [created, setCreated] = useState<{
    username: string
    nickname?: string
    avatarUrl?: string
  } | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors }
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      role: '',
      username: '',
      email: '',
      nickname: '',
      password: '',
      confirmPassword: ''
    }
  })

  const role = watch('role')

  async function onSubmit(values: FormValues) {
    // 上面的 min(1) 已经拦过空值了，这里再判一次是为了把 string 收窄成 RegisterRole，
    // 而不是拿 `as` 把类型系统一关了事。
    if (!isRegisterRole(values.role)) return

    setLoading(true)
    try {
      const user = await registerUser({
        username: values.username,
        email: values.email,
        nickname: values.nickname || undefined,
        password: values.password,
        role: values.role
      })
      toast.success(`注册成功，已赠送 ${SIGNUP_BONUS_CREDITS} 次 AI 辅助开发额度，请登录`)
      // 不立刻切回登录卡：先让用户看一眼随机分配到的头像（见 created 的注释）。
      // 用户名也不会带到登录卡上 —— 两张卡是两个组件，切换等于卸载重建，
      // 登录卡拿到的是全新的空表单。想让它带过去得把用户名塞进 uiStore，
      // 属于体验改动，迁移期间不夹带。
      setCreated({
        username: user.username,
        nickname: user.nickname,
        avatarUrl: user.avatarUrl
      })
    } catch {
      // 错误提示由 axios 拦截器统一弹出
    } finally {
      setLoading(false)
    }
  }

  if (created) {
    return (
      <div
        className="w-[520px] max-w-full overflow-y-auto rounded-xl border border-border bg-card px-8 pt-8 pb-7 shadow-2xl"
        style={{ animation: 'auth-card-in 0.35s cubic-bezier(0.4,0,0.2,1)' }}
      >
        <header className="mb-5 text-center">
          <div className="mx-auto mb-3.5 flex size-15 items-center justify-center rounded-xl bg-foreground text-background shadow-lg shadow-foreground/20">
            <img src="/logo-white.png" alt="" className="h-8 w-auto" />
          </div>
          <h1 className="text-xl font-bold tracking-tight">注册成功</h1>
          <p className="mt-1.5 text-[13px] text-muted-foreground">账号已创建，可以登录了</p>
        </header>

        <div className="rounded-xl border border-border bg-muted/40 px-6 py-8 text-center">
          <UserAvatar
            avatarUrl={created.avatarUrl}
            name={created.nickname || created.username}
            className="mx-auto mb-4 size-20"
          />
          <p className="text-base font-semibold">{created.nickname || created.username}</p>
          <p className="mt-1 text-[13px] text-muted-foreground">
            这是系统为你随机分配的头像
          </p>
        </div>

        <Button
          type="button"
          onClick={() => switchAuthCard('login')}
          className="mt-5 h-12 w-full text-base font-semibold"
        >
          去登录
        </Button>
      </div>
    )
  }

  return (
    <div
      className="w-[520px] max-w-full overflow-y-auto rounded-xl border border-border bg-card px-8 pt-8 pb-7 shadow-2xl"
      style={{ animation: 'auth-card-in 0.35s cubic-bezier(0.4,0,0.2,1)' }}
    >
      <header className="mb-5 text-center">
        <div className="mx-auto mb-3.5 flex size-15 items-center justify-center rounded-xl bg-foreground text-background shadow-lg shadow-foreground/20">
          <img src="/logo-white.png" alt="" className="h-8 w-auto" />
        </div>
        <h1 className="text-xl font-bold tracking-tight">注册账号</h1>
        <p className="mt-1.5 text-[13px] font-medium text-amber-700">{SIGNUP_PROMO_TITLE}</p>
      </header>

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="mb-4.5 space-y-3.5">
        {/*
          身份选择。放在所有输入框之前，因为它是这张表单上唯一一个"选错要重注册"
          的字段 —— 用户名错了可以改，身份注册完只能找管理员改。
        */}
        <div>
          <div className="grid grid-cols-2 gap-3.5">
            {ROLE_OPTIONS.map(({ value, title, desc, Icon }) => {
              const active = role === value
              return (
                <button
                  key={value}
                  type="button"
                  disabled={loading}
                  onClick={() => setValue('role', value, { shouldValidate: true })}
                  className={[
                    'relative cursor-pointer overflow-hidden rounded-md border-[1.5px] px-3 pt-4 pb-3.5 text-center transition-all duration-200',
                    active
                      ? 'border-foreground bg-foreground/8 shadow-[0_0_0_3px_color-mix(in_oklch,var(--foreground)_15%,transparent)]'
                      : 'border-border bg-muted/50 hover:-translate-y-0.5 hover:border-foreground/40'
                  ].join(' ')}
                >
                  {active && (
                    <span className="absolute top-0 right-0 flex size-6.5 items-center justify-center rounded-bl-md bg-foreground text-background">
                      <Check size={12} />
                    </span>
                  )}
                  <span
                    className={[
                      'mx-auto mb-2.5 flex size-11 items-center justify-center rounded-md bg-foreground/12 text-foreground transition-transform duration-200',
                      active ? 'scale-110' : ''
                    ].join(' ')}
                  >
                    <Icon size={24} />
                  </span>
                  <span className="block text-sm font-semibold">{title}</span>
                  <span className="mt-1 block text-xs leading-relaxed text-muted-foreground">
                    {desc}
                  </span>
                </button>
              )
            })}
          </div>
          {errors.role && <p className="mt-1.5 text-xs text-destructive">{errors.role.message}</p>}
        </div>

        {(
          [
            { name: 'username', label: '用户名', placeholder: '用户名', Icon: User, type: 'text', ac: 'username' },
            { name: 'email', label: '邮箱', placeholder: '邮箱', Icon: Mail, type: 'email', ac: 'email' },
            { name: 'nickname', label: '昵称', placeholder: '昵称（可选）', Icon: UserRound, type: 'text', ac: 'nickname' },
            { name: 'password', label: '密码', placeholder: '密码', Icon: Lock, type: 'password', ac: 'new-password' },
            { name: 'confirmPassword', label: '确认密码', placeholder: '确认密码', Icon: Lock, type: 'password', ac: 'new-password' }
          ] as const
        ).map(({ name, label, placeholder, Icon, type, ac }) => (
          <div key={name}>
            <Label htmlFor={`reg-${name}`} className="sr-only">
              {label}
            </Label>
            <div className="relative">
              <Icon
                size={16}
                className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground"
              />
              <Input
                id={`reg-${name}`}
                type={type}
                placeholder={placeholder}
                autoComplete={ac}
                disabled={loading}
                className="h-12 pl-9 text-[15px]"
                {...register(name)}
              />
            </div>
            {errors[name] && <p className="mt-1.5 text-xs text-destructive">{errors[name]?.message}</p>}
          </div>
        ))}

        <Button type="submit" disabled={loading} className="h-12 w-full text-base font-semibold">
          {loading && <LoaderCircle className="size-4 animate-spin" />}
          注册
        </Button>
      </form>

      <div className="text-center text-sm text-muted-foreground">
        <span>已有账号？</span>
        <button
          type="button"
          onClick={() => switchAuthCard('login')}
          className="ml-1 cursor-pointer font-medium text-primary hover:underline"
        >
          立即登录
        </button>
      </div>
    </div>
  )
}
