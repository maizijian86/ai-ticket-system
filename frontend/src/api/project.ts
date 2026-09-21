import axios from 'axios'

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
  create: (data) => api.post('', data),
  getById: (id) => api.get(`/${id}`),
  getMyProjects: (userId, page = 0, size = 10) => api.get('/my', { params: { userId, page, size } }),
  getByStatus: (status, page = 0, size = 10) => api.get(`/status/${status}`, { params: { page, size } }),
  update: (id, data) => api.put(`/${id}`, data),
  delete: (id) => api.delete(`/${id}`),

  // 项目状态
  start: (id) => api.put(`/${id}/start`),
  complete: (id) => api.put(`/${id}/complete`),
  archive: (id) => api.put(`/${id}/archive`),

  // AI分析
  updateAiAnalysis: (id, data) => api.put(`/${id}/ai-analysis`, data),

  // 模块管理
  createModule: (projectId, data) => api.post(`/${projectId}/modules`, data),
  getModules: (projectId) => api.get(`/${projectId}/modules`),
  updateModule: (id, data) => api.put(`/modules/${id}`, data),
  deleteModule: (id) => api.delete(`/modules/${id}`),

  // 任务管理
  createTask: (projectId, data) => api.post(`/${projectId}/tasks`, data),
  getTasks: (projectId) => api.get(`/${projectId}/tasks`),
  getTasksByStatus: (projectId, status) => api.get(`/${projectId}/tasks/status/${status}`),
  getModuleTasks: (moduleId) => api.get(`/modules/${moduleId}/tasks`),
  getSubTasks: (taskId) => api.get(`/tasks/${taskId}/subtasks`),
  updateTask: (id, data) => api.put(`/tasks/${id}`, data),
  updateTaskStatus: (id, status) => api.put(`/tasks/${id}/status`, { status }),
  deleteTask: (id) => api.delete(`/tasks/${id}`),

  // 里程碑
  createMilestone: (projectId, data) => api.post(`/${projectId}/milestones`, data),
  getMilestones: (projectId) => api.get(`/${projectId}/milestones`),
  updateMilestone: (id, data) => api.put(`/milestones/${id}`, data),
  completeMilestone: (id) => api.put(`/milestones/${id}/complete`),
  deleteMilestone: (id) => api.delete(`/milestones/${id}`),

  // 讨论
  addDiscussion: (projectId, data) => api.post(`/${projectId}/discussions`, data),
  getProjectDiscussions: (projectId) => api.get(`/${projectId}/discussions`),
  getTargetDiscussions: (targetType, targetId) => api.get(`/discussions/${targetType}/${targetId}`),

  // 统计
  getActiveProjects: () => api.get('/stats/active'),
  countByStatus: (status) => api.get(`/stats/count/${status}`)
}
