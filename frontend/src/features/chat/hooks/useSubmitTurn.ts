import { useMutation, useQueryClient } from '@tanstack/react-query'
import { submitTurn } from '../api'
import { isAxiosError } from 'axios'

export const useSubmitTurn = (roomId: string) => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (text: string) => submitTurn(roomId, text),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'turns', roomId] })
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response) {
        alert(`Ошибка ответа ИИ: ${error.response.data?.message || error.message}`)
      } else {
        alert('Ошибка при отправке: ' + (error as Error).message)
      }
    },
  })
}
