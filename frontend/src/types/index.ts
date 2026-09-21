export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  // 后端返回的是 accessToken / refreshToken 两个字段，不是单个 token。
  accessToken: string
  refreshToken: string
  userId: number
  username: string
  role: string
  nickname: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
  nickname?: string
}

export interface UserDTO {
  id: number
  username: string
  email: string
  nickname: string
  role: Role
  status: string
  lastLoginAt?: string
  createdAt?: string
}

export type Role = 'USER' | 'ADMIN'

export interface UserSkillDTO {
  id: number
  userId: number
  userName: string
  skillTags: string[]
  expertiseLevel: Record<string, number>
  totalResolved: number
  avgResolutionHours: number
  satisfactionScore: number
  currentLoad: number
  maxLoad: number
  recommendationScore?: number
  recommendationReason?: string
}

export interface PageResult<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
}

export interface ApiPageResult<T> {
  data: {
    data: T[]
    total: number
    page: number
    pageSize: number
  }
  message: string
  code: number
}

export type TicketStatus = 'OPEN' | 'ACCEPTED' | 'PENDING_APPROVAL' | 'COMPLETED' | 'REJECTED' | 'CLOSED'
export type TicketCategory = 'BUG' | 'CONSULT' | 'COMPLAINT' | 'SUGGESTION' | 'OTHER'
export type Priority = 'P0' | 'P1' | 'P2' | 'P3'
export type Urgency = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL'

export interface TicketDTO {
  id: number
  title: string
  content: string
  category?: TicketCategory
  categoryConfidence?: number
  priority?: Priority
  priorityScore?: number
  status: TicketStatus
  urgency: Urgency
  creatorId: number
  creatorName: string
  handlerId?: number
  handlerName?: string
  recommendedHandlerId?: number
  recommendReason?: string
  aiSummary?: string
  attachments?: Attachment[]
  createdAt: string
  updatedAt: string
  resolvedAt?: string
  closedAt?: string
  comments?: CommentDTO[]
  commentCount?: number
  // 开发者需求字段
  projectType?: string
  techStack?: string
  budget?: number
  deadline?: string
  detailedRequirements?: string
  // AI生成内容
  aiPrd?: string
  aiTechSuggestion?: string
  aiTaskBreakdown?: string
  aiEstimatedHours?: number
  // 接单状态
  acceptedBy?: number
  acceptedByName?: string
  isVisible?: boolean
}

export interface Attachment {
  name: string
  path: string
  size: number
  type: string
}

export interface CommentDTO {
  id: number
  ticketId: number
  userId: number
  userName: string
  content: string
  isInternal: boolean
  isAiSuggested: boolean
  references?: Reference[]
  createdAt: string
}

export interface Reference {
  source: string
  similarity: number
}

export interface CreateTicketRequest {
  title: string
  content: string
  category?: TicketCategory
  urgency?: Urgency
  attachments?: Attachment[]
  // 开发者需求字段
  projectType?: string
  techStack?: string
  budget?: number
  deadline?: string
  detailedRequirements?: string
}

export interface TicketQueryRequest {
  creatorId?: number
  handlerId?: number
  status?: TicketStatus
  category?: TicketCategory
  priority?: Priority
  keyword?: string
  page?: number
  pageSize?: number
}

export interface TicketStatsDTO {
  open: number
  processing: number
  resolved: number
  closed: number
}

export interface ClassifyResponse {
  category: string
  confidence: number
  reasoning: string
}

export interface PriorityResponse {
  priority: string
  score: number
  factors: string
}

export interface RecommendHandlerResponse {
  handlerId: number
  handlerName: string
  reason: string
  matchScore: number
}

export interface SummaryResponse {
  summary: string
  keyInfo: string
  solution: string
}

export interface SuggestReplyResponse {
  suggestedReply: string
  references: { source: string; similarity: number }[]
}

export interface AddCommentRequest {
  content: string
  isInternal?: boolean
}

export interface ChatMessageDTO {
  id: number
  ticketId: number
  userId: number
  userName: string
  content: string
  senderType: 'USER' | 'HANDLER' | 'AI'
  createdAt: string
}

export interface SendChatRequest {
  content: string
}
