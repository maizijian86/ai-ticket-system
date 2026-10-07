import request from '@/utils/axios'
import type {
  TicketDTO,
  CreateTicketRequest,
  TicketQueryRequest,
  CommentDTO,
  AddCommentRequest,
  TicketStatsDTO,
  PageResult,
  ChatMessageDTO,
  SendChatRequest,
  CompleteTicketRequest,
  ApproveTicketRequest,
  AiQuotaStatusDTO
} from '@/types'

/**
 * AI 接口专用的超时（10 分钟）。
 *
 * 这几个接口都是**同步阻塞**地调大模型，耗时以分钟计，而 axios 实例的全局
 * 默认超时只有 30 秒 —— 不逐个覆盖就必然在生成完成前被前端掐断，报
 * "timeout of 30000ms exceeded"。本机实测（deepseek-v4-pro）：
 *
 *   /ai-chat        约 29 秒   —— 正好压在 30 秒线上，所以时灵时不灵
 *   /download-prd   约 190 秒
 *   /ai-analysis    约 275 秒  —— 内部串行调两次模型（技术栈 + 任务拆解），
 *                                 且实测波动很大，有一轮超过 300 秒
 *
 * 取 10 分钟而不是贴着实测值：后端单次模型调用允许 5 分钟、失败还会重试一次，
 * ai-analysis 的最坏情况是两个调用各自跑满。宁可让用户多等，也不要等到了
 * 结果却因为客户端提前放弃而拿不到。
 */
const AI_TIMEOUT = 600000

export const ticketApi = {
  createTicket(data: CreateTicketRequest) {
    return request.post<any, TicketDTO>('/v1/tickets', data)
  },

  getTicket(id: number) {
    return request.get<any, TicketDTO>(`/v1/tickets/${id}`)
  },

  listTickets(params: TicketQueryRequest) {
    return request.get<any, PageResult<TicketDTO>>('/v1/tickets', { params })
  },

  // 此处原有 listMyTickets（/my）和 listMyAcceptedTickets（/my/accepted）两个封装。
  // 它们的唯一调用方是「我的需求」和「我接的单」两个页面，那些页面已并入大厅的
  // 卡片，故一并删除。后端这两个接口留着不动 —— 它们是别处也能用的读数口。
  //
  // 注意大厅的「我接的单」卡不再走 /my/accepted，而是走 listTickets +
  // handlerId + includeAll：「不限状态」这个语义用 listTickets 也能表达，而
  // 卡片要的"数字来自 /stats、列表来自 /tickets"两边同源，比多一个接口更稳。

  updateTicket(id: number, data: Partial<TicketDTO>) {
    return request.put<any, TicketDTO>(`/v1/tickets/${id}`, data)
  },

  deleteTicket(id: number) {
    return request.delete<any, void>(`/v1/tickets/${id}`)
  },

  // 此处原有 assignHandler / startProcessing / resolve / close / reopen 五个封装。
  // 对应的后端接口已删除（接单收敛到 pickupTicket 一处），它们在前端也从未被
  // 调用过 —— 保留只会让"接单有多条路径"的错觉延续下去。

  addComment(id: number, data: AddCommentRequest) {
    return request.post<any, CommentDTO>(`/v1/tickets/${id}/comment`, data)
  },

  getComments(id: number) {
    return request.get<any, CommentDTO[]>(`/v1/tickets/${id}/comments`)
  },

  getStats() {
    return request.get<any, TicketStatsDTO>('/v1/tickets/stats')
  },

  // 接单
  pickupTicket(id: number) {
    return request.post<any, TicketDTO>(`/v1/tickets/${id}/pickup`)
  },

  // 交付 —— 接单者填交付凭证（仓库地址），工单进入待验收
  completeTicket(id: number, data: CompleteTicketRequest) {
    return request.post<any, TicketDTO>(`/v1/tickets/${id}/complete`, data)
  },

  // 验收 —— 发布者通过或驳回，驳回必须给理由
  approveTicket(id: number, data: ApproveTicketRequest) {
    return request.post<any, TicketDTO>(`/v1/tickets/${id}/approve`, data)
  },

  // AI开发分析（技术建议 + 任务拆解，一次请求里串行调两次模型）
  analyzeTicket(id: number) {
    return request.post<any, TicketDTO>(`/v1/tickets/${id}/ai-analysis`, undefined, {
      timeout: AI_TIMEOUT
    })
  },

  // 获取聊天记录
  getChatHistory(id: number) {
    return request.get<any, ChatMessageDTO[]>(`/v1/tickets/${id}/chat`)
  },

  // 发送聊天消息
  sendChatMessage(id: number, data: SendChatRequest) {
    return request.post<any, ChatMessageDTO>(`/v1/tickets/${id}/chat`, data)
  },

  // 下载PRD文档
  downloadPrd(id: number) {
    return request.get(`/v1/tickets/${id}/download-prd`, {
      responseType: 'blob',
      timeout: AI_TIMEOUT
    })
  },

  // AI开发助手聊天
  aiChat(id: number, message: string) {
    return request.post<any, ChatMessageDTO>(`/v1/tickets/${id}/ai-chat`, { message }, {
      timeout: AI_TIMEOUT
    })
  },

  /**
   * 工单的 AI 额度状态：账户剩余次数 + 三个功能各自能不能按。
   *
   * 这个接口**不在网关的匿名白名单里**，未登录调用会 401。所以详情页只在已登录时
   * 才发这个请求（见 TicketDetail.vue 的 loadAiUsage）—— 访客看到的是"登录后可用"，
   * 不需要也不该知道别人的余额。
   */
  getAiUsage(id: number) {
    return request.get<any, AiQuotaStatusDTO>(`/v1/tickets/${id}/ai-usage`)
  }
}
