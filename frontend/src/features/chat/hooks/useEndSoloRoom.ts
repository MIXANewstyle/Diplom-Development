import { useMutation, useQueryClient } from '@tanstack/react-query'
import { endSoloRoom } from '../api'

export const useEndSoloRoom = (roomId: string) => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => endSoloRoom(roomId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'room', roomId] })
      queryClient.invalidateQueries({ queryKey: ['chat', 'rooms'] })
    },
  })
}
