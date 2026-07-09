import { apiClient } from '../../shared/api/client'
import type { Page } from '../feed/types'
import type { 
  AdminUserSummary, AdminUserDetails,
  PromoResponse, PromoCreateRequest, PromoUpdateRequest,
  AdminTransactionResponse, AdminTransactionPageResponse, AdminTransactionFilters,
  GrantRequest, SubscriptionResponse
} from './types'
import { getTags } from '../feed/api'

export { getTags }

export async function adminSearchUsers(query: string, page = 0, size = 20): Promise<Page<AdminUserSummary>> {
  const params = new URLSearchParams()
  if (query) params.append('query', query)
  params.append('page', page.toString())
  params.append('size', size.toString())

  const { data } = await apiClient.get<Page<AdminUserSummary>>(`/api/v1/admin/users?${params.toString()}`)
  return data
}

export async function getAdminUserDetails(userId: string): Promise<AdminUserDetails> {
  const { data } = await apiClient.get<AdminUserDetails>(`/api/v1/admin/users/${userId}`)
  return data
}

export async function updateUserRole(userId: string, roleId: number): Promise<void> {
  await apiClient.put(`/api/v1/admin/users/${userId}/role`, { roleId })
}

export async function updateUserStatus(userId: string, statusId: number): Promise<void> {
  await apiClient.put(`/api/v1/admin/users/${userId}/status`, { statusId })
}

export async function resetUserPassword(userId: string, password: string): Promise<void> {
  await apiClient.put(`/api/v1/admin/users/${userId}/password`, { password })
}

export async function createTag(name: string): Promise<{ id: string; name: string }> {
  const { data } = await apiClient.post<{ id: string; name: string }>('/internal/v1/admin/tags', { name })
  return data
}

export async function deleteTag(tagId: string): Promise<void> {
  await apiClient.delete(`/internal/v1/admin/tags/${tagId}`)
}

export async function listPromos(): Promise<PromoResponse[]> {
  const { data } = await apiClient.get<PromoResponse[]>('/api/v1/admin/billing/promo')
  return data
}

export async function createPromo(request: PromoCreateRequest): Promise<PromoResponse> {
  const { data } = await apiClient.post<PromoResponse>('/api/v1/admin/billing/promo', request)
  return data
}

export async function updatePromo(id: string, request: PromoUpdateRequest): Promise<PromoResponse> {
  const { data } = await apiClient.patch<PromoResponse>(`/api/v1/admin/billing/promo/${id}`, request)
  return data
}

export async function listTransactions(filters: AdminTransactionFilters): Promise<AdminTransactionPageResponse> {
  const params = new URLSearchParams()
  if (filters.userId) params.append('userId', filters.userId)
  if (filters.status) params.append('status', filters.status)
  if (filters.from) params.append('from', filters.from)
  if (filters.to) params.append('to', filters.to)
  if (filters.page !== undefined) params.append('page', filters.page.toString())
  if (filters.size !== undefined) params.append('size', filters.size.toString())

  const { data } = await apiClient.get<AdminTransactionPageResponse>(`/api/v1/admin/billing/transactions?${params.toString()}`)
  return data
}

export async function getTransaction(id: string): Promise<AdminTransactionResponse> {
  const { data } = await apiClient.get<AdminTransactionResponse>(`/api/v1/admin/billing/transactions/${id}`)
  return data
}

export async function refundTransaction(id: string): Promise<void> {
  await apiClient.post(`/api/v1/admin/billing/transactions/${id}/refund`)
}

export async function grantSubscription(userId: string, request: GrantRequest): Promise<SubscriptionResponse> {
  const { data } = await apiClient.post<SubscriptionResponse>(`/api/v1/admin/billing/subscriptions/${userId}/grant`, request)
  return data
}
