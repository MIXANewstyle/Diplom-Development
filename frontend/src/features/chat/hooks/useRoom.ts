import { useQuery } from '@tanstack/react-query'
import { getRoom } from '../api'

export const useRoom = (roomId: string) => {
  return useQuery({
    queryKey: ['chat', 'room', roomId],
    queryFn: () => getRoom(roomId),
    enabled: !!roomId,
  })
}
