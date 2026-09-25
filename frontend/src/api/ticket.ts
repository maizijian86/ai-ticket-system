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
  ApproveTicketRequest
} from '@/types'

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

  listMyTickets(page: number = 1, pageSize: number = 10) {
    return request.get<any, PageResult<TicketDTO>>('/v1/tickets/my', { params: { page, pageSize } })
  },

  /**
   * 「我接的单」—— 后端 /my/accepted = handler_id = 我，**不限状态**。
   *
   * 「不管完不完成」正是这个菜单要的语义。不要退回用 listTickets 表达它：
   * 那条路会命中「只查大厅可见」谓词，而接单会把 is_visible 置为 false，
   * 于是接过的单一律查不出来（这正是这个菜单此前恒为空的原因）。
   */
  listMyAcceptedTickets(page: number = 1, pageSize: number = 10) {
    return request.get<any, PageResult<TicketDTO>>('/v1/tickets/my/accepted', {
      params: { page, pageSize }
    })
  },

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

  // AI开发分析
  analyzeTicket(id: number) {
    return request.post<any, TicketDTO>(`/v1/tickets/${id}/ai-analysis`)
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
  // 注意：该接口是同步生成，后端要调用大模型写完整篇 PRD，实测约 180 秒。
  // 全局默认超时是 30 秒，不覆盖的话请求必然在生成完成前就被前端掐断。
  downloadPrd(id: number) {
    return request.get(`/v1/tickets/${id}/download-prd`, {
      responseType: 'blob',
      timeout: 300000
    })
  },

  // AI开发助手聊天
  aiChat(id: number, message: string) {
    return request.post<any, ChatMessageDTO>(`/v1/tickets/${id}/ai-chat`, { message })
  }
}
