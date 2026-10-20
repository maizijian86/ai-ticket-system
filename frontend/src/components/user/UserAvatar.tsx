import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { avatarSrc } from '@/lib/avatar'
import { cn } from 'cn'

/**
 * 用户头像的唯一渲染点。
 *
 * 三处（登录卡、注册成功、大厅卡片 / 详情页的发布者）都走这里，是为了让
 * "后端存的是什么 → 前端该请求哪个地址"只在一处判断（见 lib/avatar.ts）。
 *
 * 兜底规则：`avatarUrl` 缺失或解析不出来时显示**名字首字母**，而不是一张裂图。
 * 注册时就随机发了默认头像，所以正常数据下兜底几乎不会出现；它存在是因为
 * 大厅卡片拿的是批量接口，返回里漏掉某个 id（用户被删、或请求失败）不该
 * 让卡片上出现一个 24px 的空白缺口。
 */
export function UserAvatar({
  avatarUrl,
  name,
  className
}: {
  avatarUrl?: string | null
  /** 展示名，用于 alt 与首字母兜底 */
  name?: string | null
  className?: string
}) {
  const src = avatarSrc(avatarUrl)
  const label = (name ?? '').trim()
  const initial = label ? label.charAt(0).toUpperCase() : '?'

  return (
    <Avatar className={cn('size-6', className)}>
      {src && <AvatarImage src={src} alt={label ? `${label} 的头像` : '用户头像'} />}
      <AvatarFallback className="text-[11px] font-semibold">{initial}</AvatarFallback>
    </Avatar>
  )
}
