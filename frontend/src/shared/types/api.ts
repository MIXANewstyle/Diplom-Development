export type UserRole = 'GUEST' | 'FREE' | 'BASIC' | 'AUTHOR' | 'ADMIN'

export type ApiErrorResponse = {
  message: string
  error?: string
  fieldErrors?: Record<string, string>
}

export interface AuthUser {
  id: string
  email: string
  role: UserRole
}
