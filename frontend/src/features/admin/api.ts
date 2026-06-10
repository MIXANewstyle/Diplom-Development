import { apiClient } from '../../shared/api/client'
import { searchUsers } from '../social/api'
import { getTags } from '../feed/api'

export { searchUsers, getTags }

export async function updateUserRole(userId: string, roleId: number): Promise<void> {
  await apiClient.put(`/api/v1/admin/users/${userId}/role`, { roleId })
}

export async function updateUserStatus(userId: string, statusId: number): Promise<void> {
  await apiClient.put(`/api/v1/admin/users/${userId}/status`, { statusId })
}

export async function createTag(name: string): Promise<{ id: string; name: string }> {
  const { data } = await apiClient.post<{ id: string; name: string }>('/internal/v1/admin/tags', { name })
  return data
}

export async function deleteTag(tagId: string): Promise<void> {
  await apiClient.delete(`/internal/v1/admin/tags/${tagId}`)
}
