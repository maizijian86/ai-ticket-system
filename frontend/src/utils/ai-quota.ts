/**
 * AI 辅助开发额度的前端常量与文案。
 *
 * 集中在这里，是因为这几个数字会出现在**四个不同的位置**（大厅横幅、登录卡片、
 * 详情页额度徽标、余额不足提示），散落成字面量的话改一处漏三处。
 *
 * ⚠️ 与后端必须同步：`SIGNUP_BONUS_CREDITS` 对应
 * `ticket-service/.../service/AiQuotaService.java` 的 `SIGNUP_BONUS_CREDITS`。
 * 两边不一致会出现"海报上写送 5 次、注册完到账 3 次"——这种错误用户一定会发现，
 * 而且只会认为是虚假宣传。改的时候两边一起改。
 */

/** 新用户注册赠送次数（与后端 AiQuotaService.SIGNUP_BONUS_CREDITS 一致）。 */
export const SIGNUP_BONUS_CREDITS = 5

/** 会员每期发放次数。会员功能尚未实现，这里先用作文案占位。 */
export const MEMBERSHIP_CREDITS = 30

/** 会员有效期天数。 */
export const MEMBERSHIP_DAYS = 30

/** 活动横幅 / 登录卡片上的主文案，多处复用同一份。 */
export const SIGNUP_PROMO_TITLE = `注册即送 ${SIGNUP_BONUS_CREDITS} 次 AI 辅助开发额度`

/** AI 卡片上的功能名，与后端 AiQuotaService.label() 的措辞对齐。 */
export const AI_FEATURE_LABELS = {
  ANALYSIS: 'AI 分析',
  PRD: 'PRD 生成',
  CHAT: 'AI 对话'
} as const

/** 把后端返回的过期时间渲染成"2026-11-06"这种短日期；无值时返回空串。 */
export function formatExpireDate(value?: string | null): string {
  if (!value) return ''
  // 后端给的是 LocalDateTime 的 ISO 串（无时区），直接切前 10 位比 new Date()
  // 更稳 —— 后者会把它当本地时间再回退一次时区，跨时区看到日期差一天。
  return value.slice(0, 10)
}

/** 消耗确认框的正文，把"要花几次、还剩几次、什么时候过期"一次说清。 */
export function buildConsumeConfirmText(featureLabel: string, balance: number, nextExpireAt?: string | null): string {
  const expire = formatExpireDate(nextExpireAt)
  const tail = expire ? `，最近一批 ${expire} 过期` : ''
  return `本次将消耗 1 次 AI 辅助开发额度（当前剩余 ${balance} 次${tail}）。确认使用「${featureLabel}」？`
}
