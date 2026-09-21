import axios from 'axios'
import type {
  CreateDiscussionRequest,
  CreateMilestoneRequest,
  CreateModuleRequest,
  CreateProjectRequest,
  CreateTaskRequest,
  DiscussionTargetType,
  ProjectStatus,
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
 * 写成 res.id / res.name 会拿到 undefined。本文件的方法签名无法表达这一点
 * （axios 实例未做泛型声明），改动调用方时请留意。
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

export const projectApi = {
  // 项目CRUD
  create: (data: CreateProjectRequest) => api.post('', data),
  getById: (id: number) => api.get(`/${id}`),
  getMyProjects: (userId: number, page = 0, size = 10) => api.get('/my', { params: { userId, page, size } }),
  getByStatus: (status: ProjectStatus, page = 0, size = 10) => api.get(`/status/${status}`, { params: { page, size } }),
  update: (id: number, data: UpdateProjectRequest) => api.put(`/${id}`, data),
  delete: (id: number) => api.delete(`/${id}`),

  // 项目状态
  start: (id: number) => api.put(`/${id}/start`),
  complete: (id: number) => api.put(`/${id}/complete`),
  archive: (id: number) => api.put(`/${id}/archive`),

  // AI分析
  updateAiAnalysis: (id: number, data: UpdateAiAnalysisRequest) => api.put(`/${id}/ai-analysis`, data),

  // 模块管理
  createModule: (projectId: number, data: CreateModuleRequest) => api.post(`/${projectId}/modules`, data),
  getModules: (projectId: number) => api.get(`/${projectId}/modules`),
  updateModule: (id: number, data: UpdateModuleRequest) => api.put(`/modules/${id}`, data),
  deleteModule: (id: number) => api.delete(`/modules/${id}`),

  // 任务管理
  createTask: (projectId: number, data: CreateTaskRequest) => api.post(`/${projectId}/tasks`, data),
  getTasks: (projectId: number) => api.get(`/${projectId}/tasks`),
  getTasksByStatus: (projectId: number, status: TaskStatus) => api.get(`/${projectId}/tasks/status/${status}`),
  getModuleTasks: (moduleId: number) => api.get(`/modules/${moduleId}/tasks`),
  getSubTasks: (taskId: number) => api.get(`/tasks/${taskId}/subtasks`),
  updateTask: (id: number, data: UpdateTaskRequest) => api.put(`/tasks/${id}`, data),
  updateTaskStatus: (id: number, status: TaskStatus) => api.put(`/tasks/${id}/status`, { status }),
  deleteTask: (id: number) => api.delete(`/tasks/${id}`),

  // 里程碑
  createMilestone: (projectId: number, data: CreateMilestoneRequest) => api.post(`/${projectId}/milestones`, data),
  getMilestones: (projectId: number) => api.get(`/${projectId}/milestones`),
  updateMilestone: (id: number, data: UpdateMilestoneRequest) => api.put(`/milestones/${id}`, data),
  completeMilestone: (id: number) => api.put(`/milestones/${id}/complete`),
  deleteMilestone: (id: number) => api.delete(`/milestones/${id}`),

  // 讨论
  addDiscussion: (projectId: number, data: CreateDiscussionRequest) => api.post(`/${projectId}/discussions`, data),
  getProjectDiscussions: (projectId: number) => api.get(`/${projectId}/discussions`),
  getTargetDiscussions: (targetType: DiscussionTargetType, targetId: number) => api.get(`/discussions/${targetType}/${targetId}`),

  // 统计
  getActiveProjects: () => api.get('/stats/active'),
  countByStatus: (status: ProjectStatus) => api.get(`/stats/count/${status}`)
}
