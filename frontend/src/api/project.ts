import request from '@/api/client'
import type {
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
 * 项目管理接口。
 *
 * ── 2026-10 重写（Phase 5b）──
 * 这个文件此前**自己 `axios.create()` 了一套拦截器**，与全站共用的 `api/client`
 * 是两套语义，三个后果都很隐蔽：
 *
 *   1. 没有 40102 的刷新单飞逻辑 —— token 一过期，项目页的请求直接失败，
 *      而同一时刻别的页面还在正常刷新；
 *   2. 不注入 X-User-Id；
 *   3. 拦截器返回的是**原始信封** `{code, message, data}`，且 `code !== 200`
 *      不会被当成错误。于是每个调用点都得自己写 `res.data` 并手工判 code ——
 *      漏判一次，一次失败的业务调用就变成了"成功"。
 *
 * 现在统一走 `request`：它已经做完了上面三件事，业务码非 200 会 reject，
 * 调用方拿到的是解包后的 `data`。顺带把 `ApiEnvelope` 的手工收窄也去掉了
 * （那是为了绕开自建实例的类型错位，现在没有错位了）。
 *
 * 注意 `getMyProjects` / `getByStatus` 返回的是 **Spring Data 的 Page**，
 * 形状是 `{content, totalElements, ...}`，不是本项目自带的 `PageResult`
 * （那个是 `{data, total, page, pageSize}`）。这个差别来自后端两个控制器写法不同，
 * 不是前端能统一的 —— 所以类型上保留 `SpringPage`。
 */
export const projectApi = {
  // 项目 CRUD
  create(data: CreateProjectRequest) {
    return request.post<any, ProjectDTO>('/v1/projects', data)
  },
  getById(id: number) {
    return request.get<any, ProjectDTO>(`/v1/projects/${id}`)
  },
  getMyProjects(userId: number, page = 0, size = 10) {
    return request.get<any, SpringPage<ProjectDTO>>('/v1/projects/my', {
      params: { userId, page, size }
    })
  },
  getByStatus(status: ProjectStatus, page = 0, size = 10) {
    return request.get<any, SpringPage<ProjectDTO>>(`/v1/projects/status/${status}`, {
      params: { page, size }
    })
  },
  update(id: number, data: UpdateProjectRequest) {
    return request.put<any, ProjectDTO>(`/v1/projects/${id}`, data)
  },
  delete(id: number) {
    return request.delete<any, null>(`/v1/projects/${id}`)
  },

  // 项目状态流转
  start(id: number) {
    return request.put<any, ProjectDTO>(`/v1/projects/${id}/start`)
  },
  complete(id: number) {
    return request.put<any, ProjectDTO>(`/v1/projects/${id}/complete`)
  },
  archive(id: number) {
    return request.put<any, ProjectDTO>(`/v1/projects/${id}/archive`)
  },

  // AI 分析结果回填（分析本身在别处触发）
  updateAiAnalysis(id: number, data: UpdateAiAnalysisRequest) {
    return request.put<any, ProjectDTO>(`/v1/projects/${id}/ai-analysis`, data)
  },

  // 模块
  createModule(projectId: number, data: CreateModuleRequest) {
    return request.post<any, ProjectModuleDTO>(`/v1/projects/${projectId}/modules`, data)
  },
  getModules(projectId: number) {
    return request.get<any, ProjectModuleDTO[]>(`/v1/projects/${projectId}/modules`)
  },
  updateModule(id: number, data: UpdateModuleRequest) {
    return request.put<any, ProjectModuleDTO>(`/v1/projects/modules/${id}`, data)
  },
  deleteModule(id: number) {
    return request.delete<any, null>(`/v1/projects/modules/${id}`)
  },

  // 任务
  createTask(projectId: number, data: CreateTaskRequest) {
    return request.post<any, ProjectTaskDTO>(`/v1/projects/${projectId}/tasks`, data)
  },
  getTasks(projectId: number) {
    return request.get<any, ProjectTaskDTO[]>(`/v1/projects/${projectId}/tasks`)
  },
  getTasksByStatus(projectId: number, status: TaskStatus) {
    return request.get<any, ProjectTaskDTO[]>(`/v1/projects/${projectId}/tasks/status/${status}`)
  },
  getModuleTasks(moduleId: number) {
    return request.get<any, ProjectTaskDTO[]>(`/v1/projects/modules/${moduleId}/tasks`)
  },
  getSubTasks(taskId: number) {
    return request.get<any, ProjectTaskDTO[]>(`/v1/projects/tasks/${taskId}/subtasks`)
  },
  updateTask(id: number, data: UpdateTaskRequest) {
    return request.put<any, ProjectTaskDTO>(`/v1/projects/tasks/${id}`, data)
  },
  updateTaskStatus(id: number, status: TaskStatus) {
    return request.put<any, ProjectTaskDTO>(`/v1/projects/tasks/${id}/status`, { status })
  },
  deleteTask(id: number) {
    return request.delete<any, null>(`/v1/projects/tasks/${id}`)
  },

  // 里程碑
  createMilestone(projectId: number, data: CreateMilestoneRequest) {
    return request.post<any, MilestoneDTO>(`/v1/projects/${projectId}/milestones`, data)
  },
  getMilestones(projectId: number) {
    return request.get<any, MilestoneDTO[]>(`/v1/projects/${projectId}/milestones`)
  },
  updateMilestone(id: number, data: UpdateMilestoneRequest) {
    return request.put<any, MilestoneDTO>(`/v1/projects/milestones/${id}`, data)
  },
  /** 后端把状态置为 ACHIEVED，不是 COMPLETED —— 见 MilestoneStatus 的注释 */
  completeMilestone(id: number) {
    return request.put<any, MilestoneDTO>(`/v1/projects/milestones/${id}/complete`)
  },
  deleteMilestone(id: number) {
    return request.delete<any, null>(`/v1/projects/milestones/${id}`)
  },

  // 讨论
  addDiscussion(projectId: number, data: CreateDiscussionRequest) {
    return request.post<any, ProjectDiscussionDTO>(`/v1/projects/${projectId}/discussions`, data)
  },
  getProjectDiscussions(projectId: number) {
    return request.get<any, ProjectDiscussionDTO[]>(`/v1/projects/${projectId}/discussions`)
  },
  getTargetDiscussions(targetType: DiscussionTargetType, targetId: number) {
    return request.get<any, ProjectDiscussionDTO[]>(
      `/v1/projects/discussions/${targetType}/${targetId}`
    )
  },

  // 统计
  getActiveProjects() {
    return request.get<any, ProjectDTO[]>('/v1/projects/stats/active')
  },
  countByStatus(status: ProjectStatus) {
    return request.get<any, number>(`/v1/projects/stats/count/${status}`)
  }
}
