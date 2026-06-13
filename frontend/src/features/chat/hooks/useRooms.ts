import { useQuery } from '@tanstack/react-query'
import axios from 'axios'
import { listRooms } from '../api'
import { useAuthStore } from '../../../shared/stores/authStore'

export const useRooms = (page = 0, size = 20) => {
  const token = useAuthStore((state) => state.token)

  return useQuery({
    queryKey: ['chat', 'rooms', page, size],
    queryFn: () => listRooms(page, size),
    enabled: !!token,
    retry: (failureCount, error) => {
      if (failureCount >= 3) return false
      const status = axios.isAxiosError(error) ? error.response?.status : undefined
      return status === 500 || status === 503
    },
  })
}
