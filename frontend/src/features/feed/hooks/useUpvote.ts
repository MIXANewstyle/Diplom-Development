import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toggleUpvote } from '../api'
import { getErrorMessage } from '../../../shared/lib/errors'

export function useUpvote() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: toggleUpvote,
    onSuccess: (_data, postId) => {
      queryClient.invalidateQueries({ queryKey: ['feed'] })
      queryClient.invalidateQueries({ queryKey: ['post', postId], exact: true })
    },
    onError: (error) => {
      alert(getErrorMessage(error))
    },
  })
}
