import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import type {
  ApiEnvelope,
  CreateDiscussionRequest,
  CreateMilestoneRequest,
  CreateModuleRequest,
  CreateProjectRequest,
  CreateTaskRequest,
  DiscussionTargetType,
  MilestoneDTO,
  ProjectDiscussionDTO,
  ProjectDTO,
  ProjectModuleDTO,
  ProjectStatus,
  ProjectTaskDTO,
  SpringPage,
  TaskStatus,
  UpdateAiAnalysisRequest,
  UpdateMilestoneRequest,
  UpdateModuleRequest,
  UpdateProjectRequest,
  UpdateTaskRequest
} from '@/types'

/**
 * 注意：这个实例的响应拦截器返回的是 response.data，也就是后端统一信封
 * { code, message, data } —— 不是 data 本身。所以调用方要写 res.data，
 * 写成 res.id / res.name 会拿到 undefined。
 *
 * axios 的类型系统表达不了「拦截器把 AxiosResponse 换成了别的东西」，默认仍认为
 * 返回 AxiosResponse，于是 res.data 变成 any、res.message 变成「不存在」——两个方向
 * 都错。下面用 envelope.* 把每个方法的返回类型收窄成真正的信封，让类型和运行时一致。
 */

const api = axios.create({
  baseURL: '/api/v1/projects',
  timeout: 30000
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
api.interceptors.response.use(
  (response) => response.data,
  (error) => Promise.reject(error)
)

/** 把 axios 的返回类型改写成拦截器实际交出来的信封。运行时没有任何额外包装。 */
const envelope = {
  get: <T>(url: string, config?: AxiosRequestConfig) =>
    api.get(url, config) as unknown as Promise<ApiEnvelope<T>>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    api.post(url, data, config) as unknown as Promise<ApiEnvelope<T>>,
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    api.put(url, data, config) as unknown as Promise<ApiEnvelope<T>>,
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    api.delete(url, config) as unknown as Promise<ApiEnvelope<T>>
}

export const projectApi = {
  // 项目CRUD
  create: (data: CreateProjectRequest) => envelope.post<ProjectDTO>('', data),
  getById: (id: number) => envelope.get<ProjectDTO>(`/${id}`),
  // 后端返回 Spring Data 的 Page<Project>，序列化后是 { content, totalElements, ... }，
  // 不是本项目自带的 PageResult。
  getMyProjects: (userId: number, page = 0, size = 10) =>
    envelope.get<SpringPage<ProjectDTO>>('/my', { params: { userId, page, size } }),
  getByStatus: (status: ProjectStatus, page = 0, size = 10) =>
    envelope.get<SpringPage<ProjectDTO>>(`/status/${status}`, { params: { page, size } }),
  update: (id: number, data: UpdateProjectRequest) => envelope.put<ProjectDTO>(`/${id}`, data),
  delete: (id: number) => envelope.delete<null>(`/${id}`),

  // 项目状态
  start: (id: number) => envelope.put<ProjectDTO>(`/${id}/start`),
  complete: (id: number) => envelope.put<ProjectDTO>(`/${id}/complete`),
  archive: (id: number) => envelope.put<ProjectDTO>(`/${id}/archive`),

  // AI分析
  updateAiAnalysis: (id: number, data: UpdateAiAnalysisRequest) =>
    envelope.put<ProjectDTO>(`/${id}/ai-analysis`, data),

  // 模块管理
  createModule: (projectId: number, data: CreateModuleRequest) =>
    envelope.post<ProjectModuleDTO>(`/${projectId}/modules`, data),
  getModules: (projectId: number) => envelope.get<ProjectModuleDTO[]>(`/${projectId}/modules`),
  updateModule: (id: number, data: UpdateModuleRequest) =>
    envelope.put<ProjectModuleDTO>(`/modules/${id}`, data),
  deleteModule: (id: number) => envelope.delete<null>(`/modules/${id}`),

  // 任务管理
  createTask: (projectId: number, data: CreateTaskRequest) =>
    envelope.post<ProjectTaskDTO>(`/${projectId}/tasks`, data),
  getTasks: (projectId: number) => envelope.get<ProjectTaskDTO[]>(`/${projectId}/tasks`),
  getTasksByStatus: (projectId: number, status: TaskStatus) =>
    envelope.get<ProjectTaskDTO[]>(`/${projectId}/tasks/status/${status}`),
  getModuleTasks: (moduleId: number) => envelope.get<ProjectTaskDTO[]>(`/modules/${moduleId}/tasks`),
  getSubTasks: (taskId: number) => envelope.get<ProjectTaskDTO[]>(`/tasks/${taskId}/subtasks`),
  updateTask: (id: number, data: UpdateTaskRequest) =>
    envelope.put<ProjectTaskDTO>(`/tasks/${id}`, data),
  updateTaskStatus: (id: number, status: TaskStatus) =>
    envelope.put<ProjectTaskDTO>(`/tasks/${id}/status`, { status }),
  deleteTask: (id: number) => envelope.delete<null>(`/tasks/${id}`),

  // 里程碑
  createMilestone: (projectId: number, data: CreateMilestoneRequest) =>
    envelope.post<MilestoneDTO>(`/${projectId}/milestones`, data),
  getMilestones: (projectId: number) => envelope.get<MilestoneDTO[]>(`/${projectId}/milestones`),
  updateMilestone: (id: number, data: UpdateMilestoneRequest) =>
    envelope.put<MilestoneDTO>(`/milestones/${id}`, data),
  // 后端把状态置为 ACHIEVED，不是 COMPLETED —— 见 MilestoneStatus 的注释。
  completeMilestone: (id: number) => envelope.put<MilestoneDTO>(`/milestones/${id}/complete`),
  deleteMilestone: (id: number) => envelope.delete<null>(`/milestones/${id}`),

  // 讨论
  addDiscussion: (projectId: number, data: CreateDiscussionRequest) =>
    envelope.post<ProjectDiscussionDTO>(`/${projectId}/discussions`, data),
  getProjectDiscussions: (projectId: number) =>
    envelope.get<ProjectDiscussionDTO[]>(`/${projectId}/discussions`),
  getTargetDiscussions: (targetType: DiscussionTargetType, targetId: number) =>
    envelope.get<ProjectDiscussionDTO[]>(`/discussions/${targetType}/${targetId}`),

  // 统计
  getActiveProjects: () => envelope.get<ProjectDTO[]>('/stats/active'),
  countByStatus: (status: ProjectStatus) => envelope.get<number>(`/stats/count/${status}`)
}
