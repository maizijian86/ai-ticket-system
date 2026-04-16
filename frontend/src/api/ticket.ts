import request from '@/utils/axios'
import type {
  TicketDTO,
  CreateTicketRequest,
  TicketQueryRequest,
  CommentDTO,
  AddCommentRequest,
  TicketStatsDTO,
  PageResult
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
  }
}
