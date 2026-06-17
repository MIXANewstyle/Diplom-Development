import { useQuery } from '@tanstack/react-query'
import { getTurns } from '../api'

export const useTurns = (
  roomId: string,
  page = 0,
  size = 50,
  authToken?: string | null
) => {
  const guestAuth = authToken ?? undefined
  return useQuery({
    queryKey: ['chat', 'turns', roomId, page, size, guestAuth ? 'guest' : 'user'],
    queryFn: () =>
      getTurns(roomId, page, size, guestAuth ? { authToken: guestAuth } : undefined),
    enabled: !!roomId,
  })
}
