import { useMutation, useQueryClient } from '@tanstack/react-query'
import { submitTurn } from '../api'
import { getErrorMessage } from '../../../shared/lib/errors'
import type { TurnsPageResponse, TurnResponse } from '../types'

export const useSubmitTurn = (roomId: string) => {
  const queryClient = useQueryClient()
  const queryKey = ['chat', 'turns', roomId]

  return useMutation({
    mutationFn: (text: string) => submitTurn(roomId, text),
    onMutate: async (text) => {
      await queryClient.cancelQueries({ queryKey })
      const previousTurns = queryClient.getQueryData<TurnsPageResponse>(queryKey)

      if (previousTurns) {
        const highestSeq = previousTurns.items.reduce((max, t) => Math.max(max, t.seq), 0)
        const optimisticTurn: TurnResponse = {
          id: `optimistic-${crypto.randomUUID()}`,
          roomId,
          seq: highestSeq + 1,
          role: 'USER',
          participantId: null,
          content: text,
          promptTokens: null,
          completionTokens: null,
          createdAt: new Date().toISOString(),
          optimistic: true,
        }
        queryClient.setQueryData<TurnsPageResponse>(queryKey, {
          ...previousTurns,
          items: [...previousTurns.items, optimisticTurn],
        })
      }
      return { previousTurns }
    },
    onError: (error, _newText, context) => {
      if (context?.previousTurns) {
        queryClient.setQueryData(queryKey, context.previousTurns)
      }
      alert(getErrorMessage(error))
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey })
    },
  })
}
