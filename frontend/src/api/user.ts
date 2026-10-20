import request from '@/api/client'
import type {
  PageResult,
  UserDTO,
  UserAvatarDTO,
  UserSkillDTO,
  UpdateSkillRequest,
  Role
} from '@/types'

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

  /**
   * 上传自定义头像。multipart，字段名固定为 `file`。
   *
   * 路径里**不带 id**：身份取网关注入的 X-User-Id。带 id 的话任何人都能改别人的头像
   * （网关只校验"是不是登录了"）。前端会先把图裁成 1:1、压到 200×200 再传，
   * 但服务端仍有 2MB 与 MIME 白名单两道校验 —— 前端压缩不可信。
   */
  uploadAvatar(file: File) {
    const form = new FormData()
    form.append('file', file)
    return request.post<any, UserDTO>('/v1/users/me/avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /**
   * 换成一个默认头像（`default:1` ~ `default:9`）。
   *
   * 后端只收这一个形态、**不接受任意 URL** —— 否则就是个存储投毒的口子：
   * 客户端能把 avatar_url 写成任何地址，别的用户看到的就是攻击者指定的内容。
   */
  setDefaultAvatar(avatarUrl: string) {
    return request.put<any, UserDTO>('/v1/users/me/avatar', { avatarUrl })
  },

  /** 接单者的技能标签 / 熟练度 / 同时可接数量。管理员可改任何人，本人可改自己 */
  updateSkills(id: number, data: UpdateSkillRequest) {
    return request.put<any, UserSkillDTO>(`/v1/users/${id}/skills`, data)
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
