import request from '@/utils/axios'
import type { PageResult, UserDTO, UserSkillDTO, Role } from '@/types'

export const userApi = {
  getUser(id: number) {
    return request.get<any, UserDTO>(`/v1/users/${id}`)
  },

  listUsers(page: number = 1, pageSize: number = 10, role?: Role) {
    return request.get<any, PageResult<UserDTO>>('/v1/users', { params: { page, pageSize, role } })
  },

  updateUser(id: number, data: Partial<UserDTO>) {
    return request.put<any, UserDTO>(`/v1/users/${id}`, data)
  },

  deleteUser(id: number) {
    return request.delete<any, void>(`/v1/users/${id}`)
  },

  getUserSkill(id: number) {
    return request.get<any, UserSkillDTO>(`/v1/users/${id}/skill`)
  },

  listHandlers() {
    return request.get<any, UserDTO[]>('/v1/users/handlers')
  },

  listAvailableHandlers() {
    return request.get<any, UserSkillDTO[]>('/v1/users/handlers/available')
  }
}
