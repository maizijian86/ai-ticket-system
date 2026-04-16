import request from '@/utils/axios'
import type { LoginRequest, LoginResponse, RegisterRequest, UserDTO } from '@/types'

export const authApi = {
  login(data: LoginRequest) {
    return request.post<any, LoginResponse>('/v1/auth/login', data)
  },

  register(data: RegisterRequest) {
    return request.post<any, UserDTO>('/v1/auth/register', data)
  }
}
