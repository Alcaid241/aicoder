import request from './request'
import type { LoginRequest, LoginResponse, RegisterRequest, User } from '@/types'

export const authApi = {
  login: (data: LoginRequest) => request.post<LoginResponse>('/admin/login', data),
  register: (data: RegisterRequest) => request.post<void>('/admin/register', data),
  getUserInfo: () => request.get<User>('/admin/user/info'),
  updateUserInfo: (data: { nickname?: string; email?: string; avatar?: string }) =>
    request.put<User>('/admin/user/info', data),
}
