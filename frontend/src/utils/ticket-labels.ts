import type { TicketCategory, TicketStatus } from '@/types'

/**
 * 工单状态 / 分类的展示映射 —— 全站唯一一份。
 *
 * 这里以前是 7 份逐字复制的 getStatusLabel/getStatusType（还有 5 份分类的），
 * 散在 AcceptedTickets / CompletedTickets / MyTickets / PendingTickets /
 * TicketChat / TicketDetail / TicketList 里。复制出来的东西必然会漂移，
 * 而且已经漂了：同一个后端的 REJECTED，6 个页面显示「已拒绝」，
 * 「待处理」显示「已驳回」—— 接单者在这一页看到"驳回理由"，翻到另一页
 * 状态却写着"已拒绝"，像两件事。
 *
 * 所以合到一处：以后再改文案只有这一个地方可改。
 */

/** 状态 -> 展示文案。以「验收驳回」这条业务线为准，统一说「已驳回」。 */
const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: '待接单',
  ACCEPTED: '开发中',
  PENDING_APPROVAL: '待验收',
  COMPLETED: '已完成',
  REJECTED: '已驳回',
  CLOSED: '已关闭'
}

/** 状态 -> el-tag 的颜色。OPEN 用 warning 是"等人来接"，PENDING_APPROVAL 也是
 *  warning 是"等发布者动作"，两者都表示"卡在某个人的下一步"，不是错误。 */
const STATUS_TAG_TYPES: Record<TicketStatus, string> = {
  OPEN: 'warning',
  ACCEPTED: 'primary',
  PENDING_APPROVAL: 'warning',
  COMPLETED: 'success',
  REJECTED: 'danger',
  CLOSED: 'info'
}

const CATEGORY_LABELS: Record<TicketCategory, string> = {
  BUG: 'Bug',
  CONSULT: '咨询',
  COMPLAINT: '投诉',
  SUGGESTION: '建议',
  OTHER: '其他'
}

const CATEGORY_TAG_TYPES: Record<TicketCategory, string> = {
  BUG: 'danger',
  CONSULT: 'primary',
  COMPLAINT: 'warning',
  SUGGESTION: 'success',
  OTHER: 'info'
}

// 入参用 string 而不是联合类型：值来自后端响应，出现未知状态时
// 要能原样显示（而不是崩掉或显示 undefined），这正是最后那个 `||` 的用处。
export function getStatusLabel(status: string) {
  return STATUS_LABELS[status as TicketStatus] || status
}

export function getStatusType(status: string) {
  return STATUS_TAG_TYPES[status as TicketStatus] || 'info'
}

export function getCategoryLabel(category: string) {
  return CATEGORY_LABELS[category as TicketCategory] || category
}

export function getCategoryType(category: string) {
  return CATEGORY_TAG_TYPES[category as TicketCategory] || 'info'
}
