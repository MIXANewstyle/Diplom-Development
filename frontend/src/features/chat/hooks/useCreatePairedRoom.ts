import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { createPairedRoom } from '../api'
import { isAxiosError } from 'axios'
import { getErrorMessage } from '../../../shared/lib/errors'

export const useCreatePairedRoom = () => {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: ({ friendUserId, title }: { friendUserId: string; title?: string }) =>
      createPairedRoom(friendUserId, title),
    onSuccess: (room) => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'rooms'] })
      navigate(`/chat/${room.id}`)
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 403) {
        alert('Для создания комнаты нужна подписка BASIC (или выше).')
      } else {
        alert('Ошибка при создании парной комнаты: ' + getErrorMessage(error))
      }
    },
  })
}
