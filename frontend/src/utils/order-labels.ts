/**
 * 会员订单状态的展示映射。
 *
 * 单独立一个文件（而不是塞进 utils/ticket-labels.ts）：那个文件是工单域的，
 * 而订单状态在**三处**要用（支付结果页、开通会员页的订单列表、个人主页），
 * 复制三份必然会漂移 —— 这正是 ticket-labels.ts 开头那段教训的由来。
 *
 * ⚠️ 注意 `PENDING` 在**别的域**也出现过同名值（项目管理的 ProjectStatus 曾经有），
 * 那个模块已整体下线，现在全局只有订单域这一个 PENDING —— 别再引入第二个同名字段。
 *
 * 与 Vue 时代那份（utils/order-labels.ts）的差别只有一处：配色从 el-tag 的色名
 * 换成了 Tailwind 类名。语义没变 —— 特别是 **CLOSED 用中性色而不是红**：
 * 关闭绝大多数是"用户自己关掉了支付页"，不是错误，标红会让用户以为出了问题。
 */

const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: '待支付',
  PAID: '已支付',
  CLOSED: '已关闭'
}

/** 与 StatusTag.tsx 同一套"淡底 + 中调边 + 深字"的三档走法。 */
const ORDER_STATUS_TONES: Record<string, string> = {
  PENDING: 'border-amber-300 bg-amber-50 text-amber-700',
  PAID: 'border-emerald-300 bg-emerald-50 text-emerald-700',
  CLOSED: 'border-border bg-muted text-muted-foreground'
}

// 入参用 string 而不是联合类型：值来自后端响应，出现未知状态时要能原样显示
// （而不是崩掉或显示 undefined），这正是最后那个 `||` 的用处。
export function getOrderStatusLabel(status: string) {
  return ORDER_STATUS_LABELS[status] || status
}

export function getOrderStatusTone(status: string) {
  return ORDER_STATUS_TONES[status] || ORDER_STATUS_TONES.CLOSED
}

/** 订单金额。后端给的是 number，两位小数是"钱"的默认读法 */
export function formatAmount(amount?: number | null) {
  return amount == null ? '-' : `¥${amount.toFixed(2)}`
}

/** 时间。后端给的是不带时区的 LocalDateTime（如 2026-10-16T15:20:00），直接 new Date 即可 */
export function formatDateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
}
