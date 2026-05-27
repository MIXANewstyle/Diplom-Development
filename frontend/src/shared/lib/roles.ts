import type { UserRole } from '../types/api'

const HIERARCHY: Record<UserRole, number> = {
  GUEST: 0,
  FREE: 1,
  BASIC: 2,
  AUTHOR: 3,
  ADMIN: 4,
}

export function hasRole(userRole: UserRole | undefined, required: UserRole): boolean {
  if (!userRole) return false
  return HIERARCHY[userRole] >= HIERARCHY[required]
}

export function canEngage(userRole: UserRole | undefined): boolean {
  return hasRole(userRole, 'BASIC')
}
