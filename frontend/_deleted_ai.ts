// 已删除文件 src/api/ai.ts 的备份（2026-09-12 按"方案 A"移除）
//
// 删除原因：本文件导出的 12 个方法全部指向后端不存在的接口
// （ai-service 的 AiController 只实现了 /generate-prd 和 /dev-advice），
// 任何调用都是 404，是纯粹的无效代码。
//
// 保留原因：frontend/ 目录在 .gitignore 中被排除，不受版本控制，
// 删除后无法用 git 恢复，故在此留档。确认不再需要可整份删除。
//
// 恢复方式：改名为 src/api/ai.ts，并在 src/api/index.ts 中加回
//   export { aiApi } from './ai'

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
  },

  // 项目分析相关
  analyzeProject(description: string) {
    return request.post<any, any>('/v1/ai/analyze-project', { description })
  },

  decomposeTasks(description: string, prd?: string) {
    return request.post<any, any>('/v1/ai/decompose-tasks', { description, prd })
  },

  estimateTask(description: string, techStack?: string) {
    return request.post<any, any>('/v1/ai/estimate-task', { description, techStack })
  },

  recommendTechStack(description: string) {
    return request.post<any, any>('/v1/ai/recommend-tech-stack', { description })
  },

  generateMilestones(description: string, estimatedDays?: number) {
    return request.post<any, any>('/v1/ai/generate-milestones', { description, estimatedDays })
  }
}
