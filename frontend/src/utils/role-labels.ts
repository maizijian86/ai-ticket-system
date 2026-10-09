/**
 * 账号身份的展示映射 —— 全站唯一一份。
 *
 * 原先这两个函数是 `views/admin/UserManage.vue` 里的**局部函数**。个人主页的
 * 「账号信息」也要显示身份，再抄一份就是第二份 —— 而这份映射必须与后端
 * `common/enums/Role.java` 保持同步，抄出来的副本一定会在后端加身份时漏改。
 * 这个仓里已经发生过一次同类事故（见 utils/ticket-labels.ts 开头那段），
 * 所以这次直接合到一处，两个页面共用。
 *
 * 与 `utils/order-labels.ts`、`utils/ticket-labels.ts` 的区别：那两个是业务单据的状态，
 * 这个是**账号身份**。身份决定大厅顶上给你看哪几张卡，不决定某个工单里你能做什么。
 */

/** 身份 -> 展示文案。USER 是存量身份（发布与接单都能做），不是"普通用户"的意思。 */
const ROLE_LABELS: Record<string, string> = {
  ADMIN: '管理员',
  PUBLISHER: '发布者',
  HANDLER: '接单者',
  // 只写"用户"不写括号说明：用户管理表格里这一列宽 100px，带上说明会换行。
  // 含义见后端 common/enums/Role.java 的 javadoc。
  USER: '用户'
}

/** 身份 -> el-tag 的颜色。每个身份一个色，便于在列表里一眼区分。 */
const ROLE_TAG_TYPES: Record<string, string> = {
  ADMIN: 'danger',
  PUBLISHER: 'success',
  HANDLER: 'warning',
  USER: 'primary'
}

// 入参用 string 而不是 Role 联合类型、并且保留 `|| role` / `|| 'info'` 的兜底：
// 库里 role 是 VARCHAR(20) 不是 MySQL ENUM，出现枚举外的值时应该显示原文
// 而不是渲染成空白。
export function getRoleLabel(role: string) {
  return ROLE_LABELS[role] || role
}

export function getRoleType(role: string) {
  return ROLE_TAG_TYPES[role] || 'info'
}
