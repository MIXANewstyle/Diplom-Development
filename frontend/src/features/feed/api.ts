import { apiClient } from '../../shared/api/client'
import type { FeedResponse, Page, SortMode, Tag, UpvoteResponse } from './types'

export async function getFeed(params: {
  sort: SortMode
  cursor?: string
  tags?: string[]
}): Promise<FeedResponse> {
  const usp = new URLSearchParams()
  usp.append('sort', params.sort)

  if (params.cursor) {
    usp.append('cursor', params.cursor)
  }
  if (params.tags && params.tags.length > 0) {
    params.tags.forEach((id) => usp.append('tagIds', id))
  }

  const response = await apiClient.get<FeedResponse>(`/api/v1/feed?${usp.toString()}`)
  return response.data
}

export async function getTags(): Promise<Page<Tag>> {
  const response = await apiClient.get<Page<Tag>>('/api/v1/tags', {
    params: { page: 0, size: 50 },
  })
  return response.data
}

export async function toggleUpvote(postId: string): Promise<UpvoteResponse> {
  const response = await apiClient.post<UpvoteResponse>(`/api/v1/posts/${postId}/upvote`)
  return response.data
}
