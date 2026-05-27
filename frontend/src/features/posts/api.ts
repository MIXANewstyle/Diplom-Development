import { apiClient } from '../../shared/api/client'
import type { Post } from '../feed/types'
import type {
  Comment,
  CommentsResponse,
  CreateCommentBody,
  UpdateCommentBody,
} from './types'

export async function getPost(id: string): Promise<Post> {
  const response = await apiClient.get<Post>(`/api/v1/posts/${id}`)
  return response.data
}

export async function getRootComments(params: {
  postId: string
  cursor?: string
}): Promise<CommentsResponse> {
  const response = await apiClient.get<CommentsResponse>(
    `/api/v1/posts/${params.postId}/comments`,
    {
      params: params.cursor ? { cursor: params.cursor } : undefined,
    },
  )
  return response.data
}

export async function getReplies(params: {
  commentId: string
  cursor?: string
}): Promise<CommentsResponse> {
  const response = await apiClient.get<CommentsResponse>(
    `/api/v1/comments/${params.commentId}/replies`,
    {
      params: params.cursor ? { cursor: params.cursor } : undefined,
    },
  )
  return response.data
}

export async function createComment(params: {
  postId: string
  body: CreateCommentBody
}): Promise<Comment> {
  const response = await apiClient.post<Comment>(
    `/api/v1/posts/${params.postId}/comments`,
    params.body,
  )
  return response.data
}

export async function updateComment(params: {
  commentId: string
  body: UpdateCommentBody
}): Promise<Comment> {
  const response = await apiClient.patch<Comment>(
    `/api/v1/comments/${params.commentId}`,
    params.body,
  )
  return response.data
}

export async function deleteComment(commentId: string): Promise<void> {
  await apiClient.delete(`/api/v1/comments/${commentId}`)
}
