import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { isTokenExpired } from '../lib/jwt'
import type { ReactNode } from 'react'
import { useEffect } from 'react'

interface ProtectedRouteProps {
  children: ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const token = useAuthStore((state) => state.token)
  const clearAuth = useAuthStore((state) => state.clearAuth)

  const isExpired = token ? isTokenExpired(token) : true

  useEffect(() => {
    if (isExpired) {
      clearAuth()
    }
  }, [isExpired, clearAuth])

  if (isExpired) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
