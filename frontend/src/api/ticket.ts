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
  SendChatRequest
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

  listPendingTickets(page: number = 1, pageSize: number = 10) {
    return request.get<any, PageResult<TicketDTO>>('/v1/tickets/pending', { params: { page, pageSize } })
  },

  updateTicket(id: number, data: Partial<TicketDTO>) {
    return request.put<any, TicketDTO>(`/v1/tickets/${id}`, data)
  },

  deleteTicket(id: number) {
    return request.delete<any, void>(`/v1/tickets/${id}`)
  },

  assignHandler(id: number, handlerId: number) {
    return request.put<any, TicketDTO>(`/v1/tickets/${id}/assign`, { handlerId })
  },

  startProcessing(id: number) {
    return request.put<any, TicketDTO>(`/v1/tickets/${id}/start`)
  },

  resolve(id: number) {
    return request.put<any, TicketDTO>(`/v1/tickets/${id}/resolve`)
  },

  close(id: number) {
    return request.put<any, TicketDTO>(`/v1/tickets/${id}/close`)
  },

  reopen(id: number) {
    return request.put<any, TicketDTO>(`/v1/tickets/${id}/reopen`)
  },

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
