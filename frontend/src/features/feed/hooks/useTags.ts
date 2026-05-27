import { useQuery } from '@tanstack/react-query'
import { getTags } from '../api'

export function useTags() {
  return useQuery({
    queryKey: ['tags'],
    queryFn: getTags,
    staleTime: 5 * 60 * 1000,
  })
}
