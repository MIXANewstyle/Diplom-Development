import { useMutation, useQueryClient } from '@tanstack/react-query'
import { joinRoom } from '../api'
import { isAxiosError } from 'axios'
import { getErrorMessage } from '../../../shared/lib/errors'

export const useJoinRoom = (roomId: string) => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => joinRoom(roomId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'rooms'] })
      queryClient.invalidateQueries({ queryKey: ['chat', 'room', roomId] })
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 403) {
        alert('Для присоединения к комнате нужна подписка BASIC (или выше).')
      } else {
        alert('Ошибка при присоединении: ' + getErrorMessage(error))
      }
    },
  })
}
