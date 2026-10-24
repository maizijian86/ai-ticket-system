export { authApi } from './auth'
export { userApi } from './user'
export { ticketApi } from './ticket'
export { membershipApi } from './membership'
// aiApi 已删除：它导出的 12 个方法全部指向不存在的后端接口
//（ai-service 只实现了 /generate-prd 与 /dev-advice），调用一律 404。
