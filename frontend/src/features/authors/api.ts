import { apiClient } from '../../shared/api/client'
import type { FeedResponse } from '../feed/types'

export interface PublicProfile {
  id: string
  username: string
  fullName: string
  avatarUrl: string | null
  bio: string | null
  contactInfo: string | null
  role: string
}

export async function getAuthorProfile(authorId: string): Promise<PublicProfile> {
  const { data } = await apiClient.get<PublicProfile>(`/api/v1/users/${authorId}/profile`)
  return data
}

export async function getAuthorPosts(params: {
  authorId: string
  cursor?: string
  pageSize?: number
  tagIds?: string[]
}): Promise<FeedResponse> {
  const usp = new URLSearchParams()
  
  if (params.cursor) {
    usp.append('cursor', params.cursor)
  }
  if (params.pageSize) {
    usp.append('pageSize', params.pageSize.toString())
  }
  if (params.tagIds && params.tagIds.length > 0) {
    params.tagIds.forEach((id) => usp.append('tagIds', id))
  }

  const response = await apiClient.get<FeedResponse>(`/api/v1/authors/${params.authorId}/posts?${usp.toString()}`)
  
  if (response.data?.items) {
    response.data.items = response.data.items.map(post => {
      if (typeof post.content === 'string' && post.content.startsWith('{')) {
        try {
          post.content = JSON.parse(post.content)
        } catch (e) {
          // Keep as string if parsing fails
        }
      }
      return post
    })
  }
  
  return response.data
}
