import { apiClient } from '../../shared/api/client'
import type { FollowedAuthor, UserBrief, MyFriends } from './types'

export async function getMyFollows(userId: string): Promise<FollowedAuthor[]> {
  const { data } = await apiClient.get<FollowedAuthor[]>(`/api/v1/users/${userId}/follows`)
  return data
}

export async function followAuthor(authorId: string): Promise<void> {
  await apiClient.post(`/api/v1/social/me/follow/${authorId}`)
}

export async function unfollowAuthor(authorId: string): Promise<void> {
  await apiClient.delete(`/api/v1/social/me/follow/${authorId}`)
}

export async function getUsersBatch(ids: string[]): Promise<UserBrief[]> {
  if (ids.length === 0) return []
  const { data } = await apiClient.post<UserBrief[]>('/api/v1/users/batch', { ids })
  return data
}

export async function getMyFriends(): Promise<MyFriends> {
  const { data } = await apiClient.get<MyFriends>('/api/v1/social/me/friends')
  return data
}

export async function sendFriendRequest(addresseeId: string): Promise<void> {
  await apiClient.post(`/api/v1/social/me/friends/request/${addresseeId}`)
}

export async function acceptFriendRequest(requesterId: string): Promise<void> {
  await apiClient.post(`/api/v1/social/me/friends/${requesterId}/accept`)
}

export async function declineFriendRequest(requesterId: string): Promise<void> {
  await apiClient.post(`/api/v1/social/me/friends/${requesterId}/decline`)
}

export async function cancelFriendRequest(addresseeId: string): Promise<void> {
  await apiClient.delete(`/api/v1/social/me/friends/${addresseeId}`)
}

export async function searchUsers(username: string): Promise<UserBrief[]> {
  const usp = new URLSearchParams({ username })
  const { data } = await apiClient.get<UserBrief[]>(`/api/v1/users/search?${usp.toString()}`)
  return data
}
