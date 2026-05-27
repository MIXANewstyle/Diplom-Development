export interface RegisterRequest {
  email: string
  password: string
  username: string
  fullName: string
}

export interface RegisterResponse {
  id: string
  email: string
  username: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
}
