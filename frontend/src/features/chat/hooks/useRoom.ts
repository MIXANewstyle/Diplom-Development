import { useQuery } from '@tanstack/react-query'
import { getRoom } from '../api'

export const useRoom = (roomId: string, authToken?: string | null) => {
  const guestAuth = authToken ?? undefined
  return useQuery({
    queryKey: ['chat', 'room', roomId, guestAuth ? 'guest' : 'user'],
    queryFn: () =>
      getRoom(roomId, guestAuth ? { authToken: guestAuth } : undefined),
    enabled: !!roomId,
  })
}
