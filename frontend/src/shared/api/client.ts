import axios from 'axios'
import { useAuthStore } from '../stores/authStore'
import { queryClient } from './queryClient'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export const apiClient = axios.create({
  baseURL,
  paramsSerializer: (params) => {
    const usp = new URLSearchParams()
    for (const [k, v] of Object.entries(params as Record<string, unknown>)) {
      if (v === undefined || v === null) continue
      if (Array.isArray(v)) v.forEach((item) => usp.append(k, String(item)))
      else usp.append(k, String(v))
    }
    return usp.toString()
  },
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const hadAuthToken = !!useAuthStore.getState().token
      if (hadAuthToken) {
        useAuthStore.getState().clearAuth()
        queryClient.clear()

        // Не делаем редирект, если мы уже находимся на странице логина или регистрации.
        // Иначе при неверном пароле (401) страница будет перезагружаться.
        const isAuthPage =
          window.location.pathname === '/login' ||
          window.location.pathname === '/register'
        if (!isAuthPage) {
          window.location.href = '/login'
        }
      }
    }

    return Promise.reject(error)
  },
)
