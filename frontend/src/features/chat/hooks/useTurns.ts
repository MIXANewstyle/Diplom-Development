import { useQuery } from '@tanstack/react-query'
import { getTurns } from '../api'

export const useTurns = (roomId: string, page = 0, size = 50) => {
  return useQuery({
    queryKey: ['chat', 'turns', roomId, page, size],
    queryFn: () => getTurns(roomId, page, size),
    enabled: !!roomId,
  })
}
