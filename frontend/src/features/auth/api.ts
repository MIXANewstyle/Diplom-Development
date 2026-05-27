import { apiClient } from '../../shared/api/client'
import type { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse } from './types'

export async function registerUser(data: RegisterRequest): Promise<RegisterResponse> {
  const response = await apiClient.post<RegisterResponse>('/api/v1/users/register', data)
  return response.data
}

export async function loginUser(data: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/api/v1/users/login', data)
  return response.data
}
