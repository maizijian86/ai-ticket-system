import request from '@/utils/axios'
import type { LoginRequest, LoginResponse, RegisterRequest, UserDTO } from '@/types'

export const authApi = {
  login(data: LoginRequest) {
    return request.post<any, LoginResponse>('/v1/auth/login', data)
  },

  register(data: RegisterRequest) {
    return request.post<any, UserDTO>('/v1/auth/register', data)
  },

  /**
   * 登出。后端会删掉 Redis 里的 refreshToken，让它在 7 天到期前就失效。
   * 服务端从网关注入的 X-User-Id 取用户，不用传参。
   */
  logout() {
    return request.post<any, void>('/v1/auth/logout')
  }
}
