import request from '@/api/client'
import type { PageResult, WalletDTO, WalletOrderDTO, WalletPayDTO, WalletTxDTO } from '@/types'

/**
 * 钱包与充值。
 *
 * 身份由网关注入的 X-User-Id 提供，这里**不传 userId** ——
 * "这是谁的钱包"由后端按当前登录态决定，让前端传等于让前端决定能看谁的钱。
 *
 * ## 金额规则（与后端一致，别在这里放宽）
 * **整数元，最小 1，最大由 `me()` 下发的 `maxRecharge` 决定。**
 * 不支持角/分：后端会拒绝，前端提前拦一道只是为了少一次往返。
 *
 * ## 余额的用途
 * 发布工单时按预算**冻结**（可用 → 冻结），验收通过才转给接单者，
 * 关闭/删除会退回。所以"我能不能发这张单"看的是 `availableAmount`，
 * 不是 `totalAmount`。
 */
export const walletApi = {
  /** 我的钱包（没有钱包行时后端会懒创建，所以这个调用本身是安全的幂等操作） */
  me() {
    return request.get<any, WalletDTO>('/v1/wallet/me')
  },

  /**
   * 创建充值订单，拿回收银台链接。
   * 链接由后端签发 —— 前端不碰私钥，也不要自己拼支付宝参数。
   */
  createOrder(amount: number) {
    return request.post<any, WalletPayDTO>('/v1/wallet/orders', { amount })
  },

  /**
   * 单笔充值单状态。结果页轮询用。
   *
   * 后端在这条路径上带**侧效应**：PENDING 超过 15 分钟时会主动查一次支付宝
   * （回调丢了也能自愈）。所以轮询要有上限，别无限打。
   */
  getOrder(outTradeNo: string) {
    return request.get<any, WalletOrderDTO>(`/v1/wallet/orders/${outTradeNo}`)
  },

  /** 我的充值记录（分页，从 1 开始） */
  listOrders(page = 1, pageSize = 10) {
    return request.get<any, PageResult<WalletOrderDTO>>('/v1/wallet/orders', {
      params: { page, pageSize }
    })
  },

  /** 我的资金流水（分页）。审计账本，只增不改 */
  listTransactions(page = 1, pageSize = 20) {
    return request.get<any, PageResult<WalletTxDTO>>('/v1/wallet/transactions', {
      params: { page, pageSize }
    })
  }
}
