import request from '@/utils/axios'
import type {
  ClassifyResponse,
  PriorityResponse,
  RecommendHandlerResponse,
  SummaryResponse,
  SuggestReplyResponse
} from '@/types'

export const aiApi = {
  classify(content: string) {
    return request.post<any, ClassifyResponse>('/v1/ai/classify', { content })
  },

  evaluatePriority(content: string, urgency: string) {
    return request.post<any, PriorityResponse>('/v1/ai/priority', { content, urgency })
  },

  recommendHandler(content: string, category?: string) {
    return request.post<any, RecommendHandlerResponse>('/v1/ai/recommend-handler', { content, category })
  },

  generateSummary(content: string) {
    return request.post<any, SummaryResponse>('/v1/ai/summary', { content })
  },

  generateTitle(content: string) {
    return request.post<any, { title: string }>('/v1/ai/generate-title', { content }, { timeout: 60000 })
  },

  suggestReply(question: string, ticketId?: number, context?: string) {
    return request.post<any, SuggestReplyResponse>('/v1/ai/suggest-reply', { question, ticketId, context })
  },

  searchKnowledge(query: string, limit: number = 10) {
    return request.get<any, any[]>('/v1/ai/knowledge/search', { params: { query, limit } })
  }
}
