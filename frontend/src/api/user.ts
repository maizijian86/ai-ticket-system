import request from '@/api/client'
import type { PageResult, UserDTO, UserAvatarDTO, UserSkillDTO, Role } from '@/types'

export const userApi = {
  getUser(id: number) {
    return request.get<any, UserDTO>(`/v1/users/${id}`)
  },

  /**
   * 按用户名查头像（匿名可读）。登录卡边输边显示用。
   * 未知用户后端返回 `data: null` 而不是 404 —— 正常输入过程不该弹报错。
   */
  getAvatarByUsername(username: string) {
    return request.get<any, UserAvatarDTO | null>('/v1/users/avatar', { params: { username } })
  },

  /** 批量头像（匿名可读），id → 头像地址。返回里没有的 id 由调用方走首字母兜底 */
  getAvatars(ids: number[]) {
    return request.get<any, Record<string, string>>('/v1/users/avatars', {
      params: { ids: ids.join(',') }
    })
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
