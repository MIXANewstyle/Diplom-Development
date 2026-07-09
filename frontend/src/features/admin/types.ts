export const ROLE_OPTIONS = [
  { id: 1, label: 'GUEST' },
  { id: 2, label: 'FREE' },
  { id: 3, label: 'BASIC' },
  { id: 4, label: 'AUTHOR' },
  { id: 5, label: 'ADMIN' },
]

export const STATUS_OPTIONS = [
  { id: 1, label: 'Активен' },
  { id: 2, label: 'Заблокирован' },
  { id: 3, label: 'Удалён' },
]

export interface AdminUserSummary {
  id: string
  email: string
  username: string | null
  fullName: string | null
  avatarUrl: string | null
  roleId: number
  statusId: number
  createdAt: string
}

export interface AdminUserDetails {
  id: string
  email: string
  roleId: number
  statusId: number
  createdAt: string
  fullName: string | null
  username: string | null
  bio: string | null
  avatarUrl: string | null
  contactInfo: string | null
  birthDate: string | null
  genderId: number | null
  profileUpdatedAt: string | null
  psychProfileFilled: boolean
  friendsCount: number
  followersCount: number
  followingCount: number
}

export interface PromoResponse {
  id: string
  code: string
  discountType: 'PERCENT' | 'FIXED'
  discountValue: number
  maxUses: number
  usedCount: number
  validFrom: string | null
  validUntil: string | null
  isActive: boolean
  createdAt: string
}

export interface PromoCreateRequest {
  code: string
  discountType: 'PERCENT' | 'FIXED'
  discountValue: number
  maxUses: number
  validFrom?: string
  validUntil?: string
}

export interface PromoUpdateRequest {
  isActive?: boolean
  maxUses?: number
  validFrom?: string
  validUntil?: string
}

export interface AdminTransactionResponse {
  id: string
  userId: string
  planCode: string
  type: 'PURCHASE' | 'RENEWAL' | 'TRIAL' | 'REFUND'
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED'
  baseAmount: number
  discountAmount: number
  amount: number
  currency: string
  promoCodeId: string | null
  provider: string
  providerPaymentId: string | null
  idempotencyKey: string
  createdAt: string
  updatedAt: string
}

export interface AdminTransactionPageResponse {
  content: AdminTransactionResponse[]
  page: {
    number: number
    size: number
    totalElements: number
    totalPages: number
  }
}

export interface AdminTransactionFilters {
  userId?: string
  status?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface GrantRequest {
  days: number
  note?: string
}

export interface SubscriptionResponse {
  id: string
  userId: string
  planCode: string
  status: string
  currentPeriodEnd: string
  cancelAtPeriodEnd: boolean
}
