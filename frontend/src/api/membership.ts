import request from '@/api/client'
import type {
  MembershipPlanDTO,
  MembershipDTO,
  MembershipOrderDTO,
  MembershipPayDTO,
  PageResult
} from '@/types'

/**
 * 会员与会员订单。
 *
 * 身份由网关注入的 X-User-Id 提供，这里**不传 userId** —— 「我的订单」的
 * `user_id = 当前用户` 是后端强制的，让前端传等于让前端决定能看谁的订单。
 */
export const membershipApi = {
  /** 套餐列表。价格与权益文案的唯一来源是后端，前端不写死 */
  plans() {
    return request.get<any, MembershipPlanDTO[]>('/v1/membership/plans')
  },

  /** 我的会员状态 + 账户余额 */
  me() {
    return request.get<any, MembershipDTO>('/v1/membership/me')
  },

  /** 下单，拿回支付宝收银台链接（链接由后端签发，前端不碰私钥） */
  createOrder(planCode: string) {
    return request.post<any, MembershipPayDTO>('/v1/membership/orders', { planCode })
  },

  /**
   * 单笔订单状态。结果页轮询用 —— 后端在这条路径上带**侧效应**：
   * PENDING 超过 15 分钟时会主动查一次支付宝兜底（回调丢了也能自愈）。
   */
  getOrder(outTradeNo: string) {
    return request.get<any, MembershipOrderDTO>(`/v1/membership/orders/${outTradeNo}`)
  },

  /** 我的订单（分页）。参数从 1 开始，与全站一致 */
  listOrders(page: number = 1, pageSize: number = 10) {
    return request.get<any, PageResult<MembershipOrderDTO>>('/v1/membership/orders', {
      params: { page, pageSize }
    })
  }
}
