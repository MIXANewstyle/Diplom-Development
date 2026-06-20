import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { createSoloRoom } from '../api'
import { isAxiosError } from 'axios'
import { getErrorMessage } from '../../../shared/lib/errors'

export const useCreateSoloRoom = () => {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  return useMutation({
    mutationFn: (title?: string) => createSoloRoom(title),
    onSuccess: (room) => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'rooms'] })
      navigate(`/chat/${room.id}`)
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 403) {
        alert('Для создания комнаты нужна подписка BASIC (или выше).')
      } else {
        alert('Ошибка при создании комнаты: ' + getErrorMessage(error))
      }
    },
  })
}
