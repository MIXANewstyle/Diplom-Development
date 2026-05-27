import type { ReactNode } from 'react'
import { useAuthStore } from '../stores/authStore'
import type { UserRole } from '../types/api'

interface RoleGateProps {
  allowed: UserRole[]
  children: ReactNode
  fallback?: ReactNode
}

export function RoleGate({ allowed, children, fallback = <div>Доступ ограничен</div> }: RoleGateProps) {
  const { user } = useAuthStore()

  if (!user || !allowed.includes(user.role)) {
    return <>{fallback}</>
  }

  return <>{children}</>
}
