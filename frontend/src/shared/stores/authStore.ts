import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { AuthUser, UserRole } from '../types/api'
import { parseJwt } from '../lib/jwt'

interface AuthState {
  token: string | null
  user: AuthUser | null
  setAuth: (token: string) => void
  clearAuth: () => void
}

interface JwtPayload {
  role: UserRole
  userId: string
  sub: string
  iat: number
  exp: number
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token) => {
        const payload = parseJwt<JwtPayload>(token)
        if (!payload || !payload.userId || !payload.sub || !payload.role) {
          throw new Error('Invalid token')
        }
        set({
          token,
          user: {
            id: payload.userId,
            email: payload.sub,
            role: payload.role,
          },
        })
      },
      clearAuth: () => set({ token: null, user: null }),
    }),
    {
      name: 'auth',
    },
  ),
)
