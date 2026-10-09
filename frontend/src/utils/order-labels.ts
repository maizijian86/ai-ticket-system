/**
 * 会员订单状态的展示映射。
 *
 * 单独立一个文件（而不是塞进 utils/ticket-labels.ts）：那个文件是工单域的，
 * 而订单状态在**两处**都要用（个人主页的「我的订单」、支付结果页），
 * 复制两份必然会漂移 —— 这正是 ticket-labels.ts 开头那段教训的由来。
 *
 * ⚠️ 注意 `PENDING` 这个词在 `types/index.ts` 里已经被 `ProjectStatus` 占用，
 * 那是项目流转的状态，与订单的 PENDING 不是一回事，别为了"复用"把两者并成一个联合类型。
 */

/** 订单状态 -> 展示文案 —— 全站唯一一份 */
const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: '待支付',
  PAID: '已支付',
  CLOSED: '已关闭'
}

/**
 * 订单状态 -> el-tag 的颜色。
 *
 * PENDING 用 warning 是"等你下一步动作"，CLOSED 用 info 而不是 danger ——
 * 关闭绝大多数是"用户自己关掉了支付页"，不是错误；把它标红会让用户以为出了问题。
 */
const ORDER_STATUS_TAG_TYPES: Record<string, string> = {
  PENDING: 'warning',
  PAID: 'success',
  CLOSED: 'info'
}

// 入参用 string 而不是联合类型：值来自后端响应，出现未知状态时要能原样显示
// （而不是崩掉或显示 undefined），这正是最后那个 `||` 的用处。
export function getOrderStatusLabel(status: string) {
  return ORDER_STATUS_LABELS[status] || status
}

export function getOrderStatusType(status: string) {
  return ORDER_STATUS_TAG_TYPES[status] || 'info'
}
