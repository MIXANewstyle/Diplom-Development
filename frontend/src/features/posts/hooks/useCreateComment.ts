import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createComment } from '../api'
import type { Comment, CreateCommentBody } from '../types'

export function useCreateComment(postId: string) {
  const queryClient = useQueryClient()

  return useMutation<Comment, unknown, CreateCommentBody>({
    mutationFn: (body) => createComment({ postId, body }),
    onSuccess: (newComment) => {
      queryClient.invalidateQueries({ queryKey: ['post', postId], exact: true })
      queryClient.invalidateQueries({ queryKey: ['post', postId, 'comments'] })
      if (newComment.parentId) {
        queryClient.invalidateQueries({
          queryKey: ['comment', newComment.parentId, 'replies'],
        })
      }
    },
  })
}
