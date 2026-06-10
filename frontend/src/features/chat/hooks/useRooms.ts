import { useQuery } from '@tanstack/react-query'
import { listRooms } from '../api'

export const useRooms = (page = 0, size = 20) => {
  return useQuery({
    queryKey: ['chat', 'rooms', page, size],
    queryFn: () => listRooms(page, size),
  })
}
