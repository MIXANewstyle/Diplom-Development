import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateComment } from '../api'
import type { Comment, UpdateCommentBody } from '../types'

type UpdateVars = {
  commentId: string
  parentId: string | null
  body: UpdateCommentBody
}

export function useUpdateComment(postId: string) {
  const queryClient = useQueryClient()

  return useMutation<Comment, unknown, UpdateVars>({
    mutationFn: ({ commentId, body }) => updateComment({ commentId, body }),
    onSuccess: (_data, vars) => {
      if (vars.parentId) {
        queryClient.invalidateQueries({
          queryKey: ['comment', vars.parentId, 'replies'],
        })
      } else {
        queryClient.invalidateQueries({ queryKey: ['post', postId, 'comments'] })
      }
    },
  })
}
