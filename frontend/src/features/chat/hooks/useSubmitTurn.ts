import { useMutation, useQueryClient } from '@tanstack/react-query'
import { submitTurn } from '../api'
import { getErrorMessage } from '../../../shared/lib/errors'

export const useSubmitTurn = (roomId: string) => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (text: string) => submitTurn(roomId, text),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'turns', roomId] })
    },
    onError: (error) => {
      alert(getErrorMessage(error))
    },
  })
}
