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
  // 交付与验收。后端一直有这几个字段并一直在序列化，这里以前漏了 ——
  // 于是「交付凭证」「驳回理由」在类型上根本不存在，页面也就没地方显示它们。
  completionProof?: string
  rejectionReason?: string
  acceptedAt?: string
  completedAt?: string
  // 接单者就是 handlerId / handlerName（上面已有）。
  // 这里原有 acceptedBy / acceptedByName —— 后端已删除这两个字段，
  // 保留它们会让 TS 静默通过 `ticket.acceptedByName`，运行时永远是 undefined。
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
  excludeCreatorId?: number
  handlerId?: number
  status?: TicketStatus
  /**
   * 多状态筛选，与 status 互斥（后端 buildSpecification 里 statuses 优先）。
   *
   * 用**逗号拼接的字符串**：statuses: 'ACCEPTED,REJECTED'。这是后端文档化、
   * 且已验证可行的一种形式。也正因如此这里声明成 string 而不是 TicketStatus[] ——
   * 传数组时能不能用取决于客户端怎么序列化（axios 会把方括号转义成 %5B%5D，
   * 后端碰巧能绑上；手写的未转义 ?statuses[]=A 则会被 Tomcat 直接 400 掉），
   * 不值得依赖。类型收成 string，让"该传什么"在编译期就只有一个答案。
   */
  statuses?: string
  category?: TicketCategory
  priority?: Priority
  keyword?: string
  /**
   * true = 跳过「只查大厅可见工单」那条谓词。
   *
   * 查「我接的单」「待处理」这类列表时必须传 true —— 接单会把 is_visible 置为
   * false，不传就一条都查不到。它只管可见性，不管归属：范围仍由 handlerId /
   * creatorId 决定，两个要一起传。
   */
  includeAll?: boolean
  page?: number
  pageSize?: number
}

/** 交付 —— 接单者提交交付凭证（仓库地址），工单转入待验收。 */
export interface CompleteTicketRequest {
  /** 交付凭证，通常是一个仓库地址 */
  completionProof: string
}

/** 验收 —— 发布者通过或驳回。 */
export interface ApproveTicketRequest {
  approved: boolean
  /** approved = false 时必填（后端 ApproveTicketRequest 的注释如此约定，
   *  但不加校验，所以前端必须自己拦一道） */
  reason?: string
}

/**
 * 字段名必须与后端 TicketStatsDTO 一致（open/accepted/pendingApproval/completed）。
 * 这里以前写的是 processing/resolved/closed，全是后端不存在的字段 —— 类型没报错，
 * 但取到的值恒为 undefined，四张统计卡里三张永远显示空白。
 */
export interface TicketStatsDTO {
  open: number
  accepted: number
  pendingApproval: number
  completed: number
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

// ============================================================ 项目域
// 字段对齐 ticket-service 的 Project / ProjectModule / ProjectTask /
// Milestone / ProjectDiscussion 实体。BaseEntity 提供 id 与三个时间戳。

export type ProjectStatus = 'PLANNING' | 'IN_PROGRESS' | 'COMPLETED' | 'ARCHIVED'
export type ProjectType = 'THESIS' | 'OUTSOURCE' | 'INTERNAL' | 'PERSONAL'
export type ModuleStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'
/**
 * 任务状态。取值以「后端会存什么 + 前端会显示什么」为准：
 * ProjectService 只显式写过 TODO / DONE，其余由 updateTaskStatus 原样存库，
 * 而看板（TaskBoard）有一列「评审中」并允许把任务移过去，所以 REVIEW 是真实可达的。
 * 曾经这里还写过一个 BLOCKED —— 前后端都没有任何地方产生或消费它，已删。
 */
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE'
/**
 * 里程碑状态。后端 ProjectService 用 setStatus("ACHIEVED") 标记完成（不是 COMPLETED），
 * MilestoneList 也按 ACHIEVED / MISSED 显示。此前这里写成 COMPLETED | DELAYED，
 * 两个值后端都不产生 —— 类型与实现各错一半，谁也发现不了。
 */
export type MilestoneStatus = 'PENDING' | 'ACHIEVED' | 'MISSED'
/** 讨论挂在谁身上：项目 / 模块 / 任务 / 里程碑 */
export type DiscussionTargetType = 'PROJECT' | 'MODULE' | 'TASK' | 'MILESTONE'

interface BaseDTO {
  id: number
  createdAt?: string
  updatedAt?: string
}

export interface ProjectDTO extends BaseDTO {
  name: string
  description?: string
  projectType?: ProjectType
  aiPrd?: string
  aiTechStack?: string
  aiEstimatedHours?: number
  aiEstimatedDays?: number
  aiRiskAnalysis?: string
  status?: ProjectStatus
  priority?: Priority
  deadline?: string
  actualStartDate?: string
  actualEndDate?: string
  progress?: number
  totalTasks?: number
  completedTasks?: number
  creatorId?: number
  creatorName?: string
  attachments?: Record<string, unknown>
}

export interface ProjectModuleDTO extends BaseDTO {
  projectId: number
  name: string
  description?: string
  sortOrder?: number
  status?: ModuleStatus
  progress?: number
  totalTasks?: number
  completedTasks?: number
}

export interface ProjectTaskDTO extends BaseDTO {
  projectId: number
  moduleId?: number
  parentTaskId?: number
  title: string
  description?: string
  taskType?: string
  status?: TaskStatus
  priority?: Priority
  estimatedHours?: number
  actualHours?: number
  aiEstimatedHours?: number
  aiSuggestion?: string
  deadline?: string
  completedAt?: string
  assigneeId?: number
  assigneeName?: string
}

export interface MilestoneDTO extends BaseDTO {
  projectId: number
  name: string
  description?: string
  dueDate?: string
  actualDate?: string
  status?: MilestoneStatus
  sortOrder?: number
}

export interface ProjectDiscussionDTO extends BaseDTO {
  projectId: number
  targetType?: DiscussionTargetType
  targetId?: number
  userId?: number
  userName?: string
  content: string
  isAiSuggested?: boolean
}

/** 后端各实体用 @RequestBody 直接收实体，所以写入类型就是字段的子集。 */
export type CreateProjectRequest = Omit<ProjectDTO, 'id' | 'createdAt' | 'updatedAt'>
export type UpdateProjectRequest = Partial<CreateProjectRequest>
export type CreateModuleRequest = Omit<ProjectModuleDTO, 'id' | 'projectId' | 'createdAt' | 'updatedAt'>
export type UpdateModuleRequest = Partial<CreateModuleRequest>
export type CreateTaskRequest = Omit<ProjectTaskDTO, 'id' | 'projectId' | 'createdAt' | 'updatedAt'>
export type UpdateTaskRequest = Partial<CreateTaskRequest>
export type CreateMilestoneRequest = Omit<MilestoneDTO, 'id' | 'projectId' | 'createdAt' | 'updatedAt'>
export type UpdateMilestoneRequest = Partial<CreateMilestoneRequest>
export type CreateDiscussionRequest = Pick<ProjectDiscussionDTO, 'content'> &
  Partial<Pick<ProjectDiscussionDTO, 'targetType' | 'targetId'>>
export type UpdateAiAnalysisRequest = Pick<ProjectDTO, 'aiPrd' | 'aiTechStack' | 'aiEstimatedHours' | 'aiEstimatedDays' | 'aiRiskAnalysis'>

// ---------------------------------------------------------------- 响应信封
/**
 * 后端统一响应体。注意 src/api/project.ts 的响应拦截器返回的是**这个信封本身**，
 * 不是 data —— 所以那边的方法签名都以 ApiEnvelope<T> 为返回类型，调用方写 res.data。
 * （src/utils/axios.ts 是另一个实例，它的拦截器会剥掉信封，语义不同，别混用。）
 */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

/**
 * Spring Data 的 Page<T> 序列化后的形状，只列出前端真正用到的字段。
 * 与后端自带的 PageResult DTO 不是一回事：那个是 { total, page, pageSize, data }。
 */
export interface SpringPage<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
