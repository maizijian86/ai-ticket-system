import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import AnimatedContent from '@/components/animation/AnimatedContent'
import { LoaderCircle, ShieldCheck, Trash2, Users } from '@/components/icons'
import { CARD_BASE, PAGE, PageHeader } from '@/components/layout/PageShell'
import { confirmDialog } from '@/components/feedback/ConfirmHost'
import { UserAvatar } from '@/components/user/UserAvatar'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/authStore'
import { formatDateTime } from '@/utils/order-labels'
import { cn } from '@/lib/utils'
import type { PageResult, Role, UserDTO } from '@/types'

/*
  用户管理（管理员）。

  ── 权限边界在后端，不在这个页面 ──
  路由上有 requireAdminLoader，但它只是"别让非管理员看到入口"。真正的拦阻在
  `UserService.updateUser / deleteUser`：非管理员改别人 403；**非管理员即便改自己，
  role / status 也会被忽略**（自我提权是最短的那条路）；删人则仅限管理员，
  且不允许删自己。这两条是 2026-10 补的 —— 此前这两个接口完全没有归属校验。

  ── 为什么表格是手写的 ──
  Phase 2 把 `components/ui/table.tsx` 当死代码删了（当时确实没有任何页面用）。
  现在需要表格，有两条路：跑 shadcn CLI 把它拉回来，或者直接写一个语义化
  `<table>`。选了后者 —— 这张表只有 5 列、没有排序/列宽拖拽/虚拟滚动，
  为它重新引入一个组件文件反而多一层需要同步的样式。
*/

const PAGE_SIZE = 10

const ROLE_OPTIONS: { value: Role | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'PUBLISHER', label: '发布者' },
  { value: 'HANDLER', label: '接单者' },
  { value: 'ADMIN', label: '管理员' },
]

const ROLE_LABELS: Record<string, string> = {
  PUBLISHER: '发布者',
  HANDLER: '接单者',
  ADMIN: '管理员',
}

const ROLE_TONES: Record<string, string> = {
  PUBLISHER: 'border-sky-300 bg-sky-50 text-sky-700',
  HANDLER: 'border-emerald-300 bg-emerald-50 text-emerald-700',
  ADMIN: 'border-violet-300 bg-violet-100 text-violet-700',
}

export default function AdminUsers() {
  const myId = useAuthStore((s) => s.userInfo?.id)

  const [role, setRole] = useState<Role | ''>('')
  const [page, setPage] = useState(1)
  const [result, setResult] = useState<PageResult<UserDTO> | null>(null)
  const [loading, setLoading] = useState(true)
  /** 正在改的那一行（user id），用来只 disable 那一行 */
  const [busyId, setBusyId] = useState<number | null>(null)

  const load = useCallback(async (targetPage: number) => {
    setLoading(true)
    try {
      const res = await userApi.listUsers(targetPage, PAGE_SIZE, role || undefined)
      setResult(res)
    } catch {
      // 拦截器已提示
      setResult({ data: [], total: 0, page: targetPage, pageSize: PAGE_SIZE })
    } finally {
      setLoading(false)
    }
  }, [role])

  useEffect(() => {
    void load(page)
  }, [load, page])

  // 换筛选回到第一页，否则会出现"筛选后停在第 3 页但总共只有 1 页"
  function changeRole(next: Role | '') {
    setRole(next)
    setPage(1)
  }

  async function changeRoleOf(user: UserDTO, next: Role) {
    if (next === user.role) return
    setBusyId(user.id)
    try {
      const updated = await userApi.updateUser(user.id, { role: next })
      setResult((r) =>
        r ? { ...r, data: r.data.map((u) => (u.id === user.id ? { ...u, ...updated } : u)) } : r
      )
      toast.success(`${updated.nickname || updated.username} 已改为${ROLE_LABELS[next] ?? next}`)
    } catch {
      // 拦截器已提示（403 会说"只能修改自己的资料"）
    } finally {
      setBusyId(null)
    }
  }

  async function removeUser(user: UserDTO) {
    const ok = await confirmDialog({
      title: '删除这个账号？',
      description: `${user.nickname || user.username}（@${user.username}）会被软删除，之后不能再登录。这个操作不可撤销。`,
      confirmText: '确认删除',
      danger: true
    })
    if (!ok) return

    setBusyId(user.id)
    try {
      await userApi.deleteUser(user.id)
      toast.success('已删除')
      void load(page)
    } catch {
      // 拦截器已提示（非管理员、删自己都会被后端拒）
    } finally {
      setBusyId(null)
    }
  }

  const total = result?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className={PAGE}>
      <AnimatedContent trigger="mount" distance={20} duration={0.5} ease="power2.out" threshold={0}>
        <PageHeader
          eyebrow="Admin · Users"
          title="用户管理"
          desc="改角色、删账号。删除是软删（保留数据），但账号会立即不能登录。"
        />
      </AnimatedContent>

      {/* 筛选 */}
      <AnimatedContent trigger="mount" distance={20} duration={0.45} ease="power2.out" delay={0.05} threshold={0}>
        <div className="mt-5 flex flex-wrap items-center gap-2">
          <Users size={15} className="mr-1 text-foreground/50" />
          {ROLE_OPTIONS.map((opt) => {
            const active = role === opt.value
            return (
              <button
                key={opt.value || 'all'}
                type="button"
                onClick={() => changeRole(opt.value)}
                className={cn(
                  'inline-flex h-8 cursor-pointer items-center rounded-full border px-3.5 text-sm font-medium transition-colors',
                  active
                    ? 'border-foreground bg-foreground text-background'
                    : 'border-border bg-card text-muted-foreground hover:text-foreground'
                )}
              >
                {opt.label}
              </button>
            )
          })}
          {result ? (<span className="ml-auto text-xs text-foreground/60 [text-shadow:0_1px_0_rgba(255,255,255,0.45)]">共 {total} 人</span>) : null}
        </div>
      </AnimatedContent>

      {/* 表格 */}
      <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" delay={0.08} threshold={0}>
        <div className={cn(CARD_BASE, 'mt-4 overflow-hidden')}>
          {loading ? (
            <div className="flex h-40 items-center justify-center text-muted-foreground">
              <LoaderCircle size={18} className="animate-spin" />
            </div>
          ) : !result || result.data.length === 0 ? (
            <p className="flex h-40 items-center justify-center text-sm text-muted-foreground">
              这个筛选下没有用户
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border text-[11px] tracking-wider text-muted-foreground uppercase">
                    <th className="px-5 py-3 font-medium">用户</th>
                    <th className="px-5 py-3 font-medium">角色</th>
                    <th className="hidden px-5 py-3 font-medium sm:table-cell">注册时间</th>
                    <th className="hidden px-5 py-3 font-medium md:table-cell">最近登录</th>
                    <th className="px-5 py-3 text-right font-medium">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {result.data.map((u) => {
                    const isMe = u.id === myId
                    const busy = busyId === u.id
                    return (
                      <tr key={u.id} className={cn('transition-colors hover:bg-muted/40', busy && 'opacity-60')}>
                        <td className="px-5 py-3">
                          <div className="flex items-center gap-2.5">
                            <UserAvatar
                              avatarUrl={u.avatarUrl}
                              name={u.nickname || u.username}
                              className="size-8"
                            />
                            <div className="min-w-0">
                              <div className="flex items-center gap-1.5">
                                <span className="truncate font-medium">
                                  {u.nickname || u.username}
                                </span>
                                {isMe ? (
                                  <span className="rounded bg-foreground/10 px-1 py-0.5 text-[10px] text-foreground/60">
                                    我
                                  </span>
                                ) : null}
                              </div>
                              <div className="truncate font-mono text-[11px] text-muted-foreground">
                                @{u.username}
                              </div>
                            </div>
                          </div>
                        </td>

                        <td className="px-5 py-3">
                          {/*
                            角色用原生 select：这是唯一"选项数量会变"的控件，
                            做成胶囊组会把 4 个档位铺满一行，与后面的操作列抢位置。
                            原生 select 在管理后台这种低审美诉求的场景里更省事，
                            也自带键盘操作。
                          */}
                          <div className="flex items-center gap-2">
                            <span
                              className={cn(
                                'inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium',
                                ROLE_TONES[u.role] ?? 'border-border bg-muted text-muted-foreground'
                              )}
                            >
                              {ROLE_LABELS[u.role] ?? u.role}
                            </span>
                            <select
                              aria-label="修改角色"
                              value={u.role}
                              disabled={busy}
                              onChange={(e) => void changeRoleOf(u, e.target.value as Role)}
                              className="h-7 cursor-pointer rounded-md border border-input bg-transparent px-1.5 text-xs outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/40 disabled:cursor-not-allowed"
                            >
                              <option value="PUBLISHER">发布者</option>
                              <option value="HANDLER">接单者</option>
                              <option value="ADMIN">管理员</option>
                            </select>
                          </div>
                        </td>

                        <td className="hidden px-5 py-3 text-xs text-muted-foreground sm:table-cell">
                          {formatDateTime(u.createdAt)}
                        </td>
                        <td className="hidden px-5 py-3 text-xs text-muted-foreground md:table-cell">
                          {u.lastLoginAt ? formatDateTime(u.lastLoginAt) : '—'}
                        </td>

                        <td className="px-5 py-3 text-right">
                          <button
                            type="button"
                            disabled={busy || isMe}
                            onClick={() => void removeUser(u)}
                            title={isMe ? '不能删除自己的账号' : '删除账号'}
                            className="inline-flex size-8 cursor-pointer items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-red-50 hover:text-red-600 disabled:cursor-not-allowed disabled:opacity-30"
                          >
                            {busy ? <LoaderCircle size={14} className="animate-spin" /> : <Trash2 size={14} />}
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </AnimatedContent>

      {/* 翻页 */}
      {totalPages > 1 ? (
        <div className="mt-3 flex items-center justify-between">
          <button
            type="button"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            className="cursor-pointer text-xs font-medium text-foreground/65 transition-colors [text-shadow:0_1px_0_rgba(255,255,255,0.45)] hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
          >
            上一页
          </button>
          <span className="text-xs text-foreground/65 [text-shadow:0_1px_0_rgba(255,255,255,0.45)]">
            {page} / {totalPages}
          </span>
          <button
            type="button"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            className="cursor-pointer text-xs font-medium text-foreground/65 transition-colors [text-shadow:0_1px_0_rgba(255,255,255,0.45)] hover:text-foreground disabled:cursor-not-allowed disabled:opacity-40"
          >
            下一页
          </button>
        </div>
      ) : null}

      <p className="mt-5 flex items-start gap-1.5 text-[11px] leading-5 text-foreground/60 [text-shadow:0_1px_0_rgba(255,255,255,0.45)]">
        <ShieldCheck size={12} className="mt-0.5 shrink-0" />
        这些接口的权限在后端校验：非管理员改别人会被 403 拒掉，非管理员改自己的角色会被忽略，
        删除只有管理员能做且不能删自己。
      </p>
    </div>
  )
}
