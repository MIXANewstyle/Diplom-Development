import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteComment } from '../api'

type DeleteVars = {
  commentId: string
  parentId: string | null
}

export function useDeleteComment(postId: string) {
  const queryClient = useQueryClient()

  return useMutation<void, unknown, DeleteVars>({
    mutationFn: ({ commentId }) => deleteComment(commentId),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ['post', postId], exact: true })
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
