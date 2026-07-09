import { useMutation, useQueryClient } from '@tanstack/react-query'
import { submitTurn } from '../api'
import { getErrorMessage } from '../../../shared/lib/errors'
import { appendOptimisticTurn, rollbackOptimisticTurn } from '../lib/optimisticTurns'

export const useSubmitTurn = (roomId: string, participantId?: string | null) => {
  const queryClient = useQueryClient()
  const queryKey = ['chat', 'turns', roomId]

  return useMutation({
    mutationFn: (text: string) => submitTurn(roomId, text),
    onMutate: async (text) => {
      await queryClient.cancelQueries({ queryKey })
      const ctx = appendOptimisticTurn(queryClient, roomId, text, participantId)
      return ctx
    },
    onError: (error, _newText, context) => {
      if (context?.snapshots) {
        rollbackOptimisticTurn(queryClient, context.snapshots)
      }
      alert(getErrorMessage(error))
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey })
    },
  })
}
