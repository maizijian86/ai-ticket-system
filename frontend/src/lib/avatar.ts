/**
 * 头像地址的唯一解析点。
 *
 * 后端 `avatar_url` 只会有两种形态（见 user-service 的 UserService.displayAvatar）：
 *   · `default:N`（N=1..9）—— 注册时随机分配、不主动改就不变的默认头像。
 *     图是前端 fixture `public/avatars/N.svg`，**不在 OSS 里**：9 张永不改变的图
 *     跟着前端发版才对，放对象存储要白付存储费和每次请求的流量。
 *   · 已是 https 开头的公网地址（自定义上传的，后端从 OSS key 拼好）。
 *
 * 解析不出来时返回 undefined，让调用方走首字母兜底。**不要退回原始字符串**：
 * 裸 OSS key（`avatar/1/x.png`）交给 <img> 会去请求当前站点下的相对路径，
 * 变成一张毫无线索的裂图。
 */
const DEFAULT_PREFIX = 'default:'
/** 与后端 UserService.DEFAULT_AVATAR_COUNT、db/migration.sql 的回填范围三处同步 */
const DEFAULT_AVATAR_COUNT = 9

export function avatarSrc(avatarUrl?: string | null): string | undefined {
  if (!avatarUrl) return undefined

  if (avatarUrl.startsWith(DEFAULT_PREFIX)) {
    const n = Number(avatarUrl.slice(DEFAULT_PREFIX.length))
    if (!Number.isInteger(n) || n < 1 || n > DEFAULT_AVATAR_COUNT) return undefined
    return `/avatars/${n}.svg`
  }

  return /^https?:\/\//i.test(avatarUrl) ? avatarUrl : undefined
}
