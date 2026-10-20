import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import AnimatedContent from '@/components/animation/AnimatedContent'
import {
  Calendar,
  Check,
  Coins,
  LoaderCircle,
  Upload,
  UserRound
} from '@/components/icons'
import { CARD_BASE, PAGE, PageHeader, SectionCard } from '@/components/layout/PageShell'
import { UserAvatar } from '@/components/user/UserAvatar'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { membershipApi } from '@/api/membership'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/authStore'
import { formatExpireDate } from '@/utils/ai-quota'
import {
  formatAmount,
  formatDateTime,
  getOrderStatusLabel,
  getOrderStatusTone
} from '@/utils/order-labels'
import { cn } from '@/lib/utils'
import type { MembershipDTO, MembershipOrderDTO, UserDTO, UserSkillDTO } from '@/types'

/*
  个人主页。

  ── 为什么它必须存在 ──
  顶栏下拉的第一项就是它；而且支付结果页的「查看我的订单」也指向这里
  （见 pages/membership/MembershipResult）—— 在它还是占位页时，用户付完钱
  点"查看我的订单"会撞进一句"施工中"。

  ── 头像为什么要在前端裁 ──
  后端有 2MB 硬上限（user-service 的 AVATAR_MAX_BYTES + multipart 配置）。
  手机相册里随手一张照片就是 3–5MB，直接传会撞上一个只说
  "Maximum upload size exceeded" 的 500。所以这里先按 1:1 居中裁切、
  缩到 200×200 再转 JPEG ——**但服务端的校验一条都不能因此省**（前端压缩不可信）。

  ── 谁能改什么 ──
  昵称/头像/技能改的都是"自己"，路径里不带 id（除了技能那条，后端要求带）。
  改 role/status 不在这个页面 —— 那是用户管理页的事，而且后端只允许管理员改。
*/

/** 头像目标边长。后端压缩注释里写的也是 200×200 */
const AVATAR_SIZE = 200

/**
 * 把用户选的图裁成 1:1 并压到 200×200 的 JPEG。
 *
 * 用离屏 canvas 做，不引第三方图库：这里只需要"居中裁方 + 缩放 + 转码"三件事，
 * 引一个库（cropper 类通常还带一套自己的 UI）反而更重。
 */
async function compressAvatar(file: File): Promise<File> {
  const bitmap = await createImageBitmap(file)
  const side = Math.min(bitmap.width, bitmap.height)
  const sx = (bitmap.width - side) / 2
  const sy = (bitmap.height - side) / 2

  const canvas = document.createElement('canvas')
  canvas.width = AVATAR_SIZE
  canvas.height = AVATAR_SIZE
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('浏览器不支持 canvas，无法压缩头像')
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(bitmap, sx, sy, side, side, 0, 0, AVATAR_SIZE, AVATAR_SIZE)
  bitmap.close?.()

  const blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/jpeg', 0.86)
  )
  if (!blob) throw new Error('图片转码失败')

  // 后端按 MIME 白名单校验，文件名带 .jpg 才不会被拒
  return new File([blob], `avatar-${Date.now()}.jpg`, { type: 'image/jpeg' })
}

export default function Profile() {
  const navigate = useNavigate()

  const userInfo = useAuthStore((s) => s.userInfo)
  const setUserInfo = useAuthStore((s) => s.setUserInfo)

  const [me, setMe] = useState<MembershipDTO | null>(null)
  const [orders, setOrders] = useState<MembershipOrderDTO[] | null>(null)
  const [skill, setSkill] = useState<UserSkillDTO | null>(null)

  // 昵称编辑
  const [nickname, setNickname] = useState(userInfo?.nickname ?? '')
  const [savingNick, setSavingNick] = useState(false)
  const nickDirty = nickname.trim() !== (userInfo?.nickname ?? '')

  // 头像
  const [uploading, setUploading] = useState(false)
  const [pickingDefault, setPickingDefault] = useState(false)
  const fileRef = useRef<HTMLInputElement | null>(null)

  // 技能（只有接单者有意义）
  const [skillDraft, setSkillDraft] = useState('')
  const [maxLoad, setMaxLoad] = useState('')
  const [savingSkill, setSavingSkill] = useState(false)

  const isHandler = userInfo?.role === 'HANDLER'

  const loadMe = useCallback(async () => {
    try {
      setMe(await membershipApi.me())
    } catch {
      /* 拦截器已提示 */
    }
  }, [])

  const loadOrders = useCallback(async () => {
    try {
      const res = await membershipApi.listOrders(1, 5)
      setOrders(res.data ?? [])
    } catch {
      setOrders([])
    }
  }, [])

  const loadSkill = useCallback(async () => {
    if (!userInfo?.id || userInfo.role !== 'HANDLER') return
    try {
      const s = await userApi.getUserSkill(userInfo.id)
      setSkill(s)
      setSkillDraft((s?.skillTags ?? []).join(', '))
      setMaxLoad(s?.maxLoad == null ? '' : String(s.maxLoad))
    } catch {
      setSkill(null)
    }
  }, [userInfo?.id, userInfo?.role])

  useEffect(() => {
    void loadMe()
    void loadOrders()
    void loadSkill()
  }, [loadMe, loadOrders, loadSkill])

  /**
   * 资料改完之后同步 store。
   *
   * 顶栏那个头像和昵称读的都是 store，不同步的话头像换了、昵称改了，
   * 顶栏还是旧的，直到刷新一次页面。
   */
  function applyUser(patch: Partial<Pick<UserDTO, 'avatarUrl' | 'nickname'>>) {
    if (!userInfo) return
    setUserInfo({ ...userInfo, ...patch })
  }

  async function handleFile(file: File | undefined) {
    if (!file) return
    setUploading(true)
    try {
      const compressed = await compressAvatar(file)
      const updated = await userApi.uploadAvatar(compressed)
      applyUser({ avatarUrl: updated.avatarUrl })
      toast.success('头像已更新')
    } catch (e) {
      // 压缩阶段的错误是我们自己的话术；上传阶段的错误拦截器已经提示过
      if (e instanceof Error && e.message.includes('头像')) toast.error(e.message)
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  async function pickDefault(n: number) {
    setPickingDefault(true)
    try {
      const updated = await userApi.setDefaultAvatar(`default:${n}`)
      applyUser({ avatarUrl: updated.avatarUrl })
      toast.success('已换用默认头像')
    } catch {
      /* 拦截器已提示 */
    } finally {
      setPickingDefault(false)
    }
  }

  async function saveNickname() {
    if (!userInfo || !nickDirty) return
    const value = nickname.trim()
    if (!value) {
      toast.error('昵称不能为空')
      return
    }
    setSavingNick(true)
    try {
      await userApi.updateUser(userInfo.id, { nickname: value })
      applyUser({ nickname: value })
      toast.success('昵称已保存')
    } catch {
      /* 拦截器已提示 */
    } finally {
      setSavingNick(false)
    }
  }

  async function saveSkill() {
    if (!userInfo) return
    const tags = skillDraft
      .split(/[,，\s]+/)
      .map((t) => t.trim())
      .filter(Boolean)
    setSavingSkill(true)
    try {
      const payload = {
        userId: userInfo.id,
        skillTags: tags,
        maxLoad: maxLoad.trim() ? Number(maxLoad) : undefined
      }
      const updated = await userApi.updateSkills(userInfo.id, payload)
      setSkill(updated)
      toast.success('技能已保存')
    } catch {
      /* 拦截器已提示 */
    } finally {
      setSavingSkill(false)
    }
  }

  return (
    <div className={PAGE}>
      <AnimatedContent trigger="mount" distance={20} duration={0.5} ease="power2.out" threshold={0}>
        <PageHeader
          eyebrow="Profile"
          title="个人主页"
          desc="头像、昵称、可用额度与历史订单都在这里。技能标签会被大厅的接单推荐参考。"
        />
      </AnimatedContent>

      <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] lg:items-start">
        {/* ── 左栏 ── */}
        <div className="flex flex-col gap-4">
          {/* 身份卡 */}
          <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" delay={0.05} threshold={0}>
            <section className={cn(CARD_BASE, 'p-6')}>
              <div className="flex items-start gap-5">
                <div className="flex flex-col items-center gap-2">
                  <UserAvatar
                    avatarUrl={userInfo?.avatarUrl}
                    name={userInfo?.nickname || userInfo?.username}
                    className="size-20"
                  />
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => fileRef.current?.click()}
                      disabled={uploading}
                      className="inline-flex cursor-pointer items-center gap-1 rounded-full border border-border px-2.5 py-1 text-[11px] font-medium text-muted-foreground transition-colors hover:text-foreground disabled:opacity-60"
                    >
                      {uploading ? (
                        <LoaderCircle size={11} className="animate-spin" />
                      ) : (
                        <Upload size={11} />
                      )}
                      上传
                    </button>
                  </div>
                  <input
                    ref={fileRef}
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    className="hidden"
                    onChange={(e) => void handleFile(e.target.files?.[0])}
                  />
                </div>

                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="truncate text-lg font-semibold tracking-tight">
                      {userInfo?.nickname || userInfo?.username || '未登录'}
                    </h2>
                    <RoleChip role={userInfo?.role} />
                  </div>
                  <p className="mt-0.5 font-mono text-xs text-muted-foreground">
                    @{userInfo?.username}
                  </p>

                  <div className="mt-4">
                    <Label htmlFor="profile-nickname" className="text-xs text-muted-foreground">
                      昵称
                    </Label>
                    <div className="mt-1.5 flex items-center gap-2">
                      <Input
                        id="profile-nickname"
                        value={nickname}
                        onChange={(e) => setNickname(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') void saveNickname()
                        }}
                        maxLength={100}
                        placeholder="别人在大厅看到的名字"
                        className="h-9 text-sm"
                      />
                      <button
                        type="button"
                        onClick={() => void saveNickname()}
                        disabled={!nickDirty || savingNick}
                        className="inline-flex h-9 shrink-0 cursor-pointer items-center gap-1 rounded-full bg-foreground px-4 text-sm font-medium text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-35"
                      >
                        {savingNick ? <LoaderCircle size={13} className="animate-spin" /> : <Check size={13} />}
                        保存
                      </button>
                    </div>
                    <p className="mt-1.5 text-[11px] text-muted-foreground">
                      用户名不可修改；昵称会显示在大厅卡片与工单详情。
                    </p>
                  </div>
                </div>
              </div>

              {/* 默认头像九宫格：没有上传条件时也能换一个 */}
              <div className="mt-5 border-t border-border pt-4">
                <p className="mb-2 text-xs text-muted-foreground">换一个默认头像</p>
                <div className="flex flex-wrap gap-2">
                  {Array.from({ length: 9 }, (_, i) => i + 1).map((n) => {
                    const active = userInfo?.avatarUrl === `default:${n}`
                    return (
                      <button
                        key={n}
                        type="button"
                        disabled={pickingDefault}
                        onClick={() => void pickDefault(n)}
                        aria-label={`默认头像 ${n}`}
                        className={cn(
                          'size-10 overflow-hidden rounded-full border-2 transition-all hover:scale-105 disabled:opacity-50',
                          active ? 'border-foreground' : 'border-transparent hover:border-border'
                        )}
                      >
                        <img src={`/avatars/${n}.svg`} alt="" className="size-full object-cover" />
                      </button>
                    )
                  })}
                </div>
              </div>
            </section>
          </AnimatedContent>

          {/* 技能（仅接单者） */}
          {isHandler ? (
            <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" delay={0.08} threshold={0}>
              <SectionCard title="技能与接单设置">
                <div className="space-y-4">
                  <div>
                    <div className="mb-1.5 flex items-baseline justify-between gap-3">
                      <Label htmlFor="profile-skills" className="text-sm font-medium">
                        技能标签
                      </Label>
                      <span className="text-[11px] text-muted-foreground">逗号或空格分隔</span>
                    </div>
                    <Input
                      id="profile-skills"
                      value={skillDraft}
                      onChange={(e) => setSkillDraft(e.target.value)}
                      placeholder="例如：Spring Boot, Vue, MySQL"
                      className="h-9 text-sm"
                    />
                    {skill?.skillTags?.length ? (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {skill.skillTags.map((t) => (
                          <span
                            key={t}
                            className="rounded border border-border bg-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground"
                          >
                            {t}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <Label htmlFor="profile-maxload" className="text-sm font-medium">
                        同时可接单数
                      </Label>
                      <Input
                        id="profile-maxload"
                        inputMode="numeric"
                        value={maxLoad}
                        onChange={(e) => setMaxLoad(e.target.value)}
                        placeholder="例如：3"
                        className="mt-1.5 h-9 text-sm"
                      />
                    </div>
                    {/* 后端算出来的接单画像：只读，但是接单者最想看的一栏 */}
                    <div>
                      <p className="text-sm font-medium">当前负载</p>
                      <p className="mt-1.5 text-sm text-muted-foreground">
                        {skill ? `${skill.currentLoad ?? 0} / ${skill.maxLoad ?? '—'}` : '—'}
                        {skill?.totalResolved ? (
                          <span className="ml-2 text-xs">累计完成 {skill.totalResolved}</span>
                        ) : null}
                      </p>
                    </div>
                  </div>

                  <div className="flex justify-end">
                    <button
                      type="button"
                      onClick={() => void saveSkill()}
                      disabled={savingSkill}
                      className="inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-full bg-foreground px-4 text-sm font-medium text-background transition-opacity hover:opacity-90 disabled:opacity-50"
                    >
                      {savingSkill ? <LoaderCircle size={13} className="animate-spin" /> : null}
                      保存技能
                    </button>
                  </div>
                </div>
              </SectionCard>
            </AnimatedContent>
          ) : null}
        </div>

        {/* ── 右栏 ── */}
        <div className="flex flex-col gap-4">
          {/* 额度 */}
          <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" delay={0.06} threshold={0}>
            <section className={cn(CARD_BASE, 'p-6')}>
              <div className="flex items-center justify-between">
                <h2 className="flex items-center gap-2 text-sm font-semibold">
                  <Coins size={15} className="text-muted-foreground" />
                  AI 额度
                </h2>
                <button
                  type="button"
                  onClick={() => navigate('/membership')}
                  className="cursor-pointer text-xs font-medium text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
                >
                  {me?.isMember ? '续期' : '开通会员'}
                </button>
              </div>

              <div className="mt-4 flex items-baseline gap-1.5">
                <span className="text-3xl font-semibold tracking-tight">{me ? me.remaining : '—'}</span>
                <span className="text-sm text-muted-foreground">次可用</span>
              </div>

              <div className="mt-3 space-y-1.5 text-xs text-muted-foreground">
                <p className="flex items-center gap-1.5">
                  <UserRound size={12} />
                  {me?.isMember ? `${me.planCode ?? '会员'} · 有效` : '当前不是会员'}
                </p>
                {me?.isMember && me.expiresAt ? (
                  <p className="flex items-center gap-1.5">
                    <Calendar size={12} />
                    到期 {formatExpireDate(me.expiresAt)}
                  </p>
                ) : null}
                {me?.nextExpireAt ? (
                  <p>最近一批 {formatExpireDate(me.nextExpireAt)} 过期，到期未用会作废。</p>
                ) : null}
              </div>
            </section>
          </AnimatedContent>

          {/* 订单 */}
          <AnimatedContent trigger="mount" distance={24} duration={0.5} ease="power2.out" delay={0.09} threshold={0}>
            <section className={cn(CARD_BASE, 'p-6')}>
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold">最近订单</h2>
                <button
                  type="button"
                  onClick={() => navigate('/membership')}
                  className="cursor-pointer text-xs font-medium text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
                >
                  全部订单
                </button>
              </div>

              <div className="mt-3">
                {orders === null ? (
                  <div className="flex h-20 items-center justify-center text-muted-foreground">
                    <LoaderCircle size={16} className="animate-spin" />
                  </div>
                ) : orders.length === 0 ? (
                  <p className="flex h-20 items-center justify-center text-sm text-muted-foreground">
                    还没有订单
                  </p>
                ) : (
                  <ul className="divide-y divide-border">
                    {orders.map((o) => (
                      <li key={o.orderId} className="flex items-center gap-2 py-2.5">
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5">
                            <span className="text-sm font-medium">{o.planCode}</span>
                            <span
                              className={cn(
                                'inline-flex items-center rounded-full border px-1.5 py-0.5 text-[10px] font-medium',
                                getOrderStatusTone(o.status)
                              )}
                            >
                              {getOrderStatusLabel(o.status)}
                            </span>
                          </div>
                          <div className="mt-0.5 text-[11px] text-muted-foreground">
                            {formatDateTime(o.createdAt)}
                          </div>
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          <span className="text-sm font-semibold">{formatAmount(o.amount)}</span>
                          <button
                            type="button"
                            onClick={() =>
                              navigate(
                                `/membership/result?outTradeNo=${encodeURIComponent(o.outTradeNo)}`
                              )
                            }
                            className="cursor-pointer text-[11px] text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
                          >
                            {o.status === 'PENDING' ? '去支付' : '查看'}
                          </button>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </section>
          </AnimatedContent>
        </div>
      </div>
    </div>
  )
}

/** 角色徽标。三种身份的措辞与大厅筛选卡的叫法保持一致。 */
function RoleChip({ role }: { role?: string }) {
  const map: Record<string, { label: string; className: string }> = {
    PUBLISHER: { label: '发布者', className: 'border-sky-300 bg-sky-50 text-sky-700' },
    HANDLER: { label: '接单者', className: 'border-emerald-300 bg-emerald-50 text-emerald-700' },
    ADMIN: { label: '管理员', className: 'border-violet-300 bg-violet-100 text-violet-700' },
    USER: { label: '普通用户', className: 'border-border bg-muted text-muted-foreground' }
  }
  const item = role ? map[role] : undefined
  if (!item) return null
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium',
        item.className
      )}
    >
      {item.label}
    </span>
  )
}
