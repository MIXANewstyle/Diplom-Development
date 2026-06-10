import { apiClient } from '../../shared/api/client'
import type { MyPost, PostFormValues } from './types'

export async function getMyPosts(status?: string): Promise<MyPost[]> {
  const url = status ? `/api/v1/posts/mine?status=${status}` : '/api/v1/posts/mine'
  const { data } = await apiClient.get<MyPost[]>(url)
  return data
}

export async function createPost(body: PostFormValues): Promise<MyPost> {
  const { data } = await apiClient.post<MyPost>('/api/v1/posts', body)
  return data
}

export async function updatePost(postId: string, body: PostFormValues): Promise<MyPost> {
  const { data } = await apiClient.patch<MyPost>(`/api/v1/posts/${postId}`, body)
  return data
}

export async function publishPost(postId: string): Promise<MyPost> {
  const { data } = await apiClient.post<MyPost>(`/api/v1/posts/${postId}/publish`)
  return data
}

export async function archivePost(postId: string): Promise<MyPost> {
  const { data } = await apiClient.post<MyPost>(`/api/v1/posts/${postId}/archive`)
  return data
}

export async function deletePost(postId: string): Promise<void> {
  await apiClient.delete(`/api/v1/posts/${postId}`)
}
