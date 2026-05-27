import { useQuery } from '@tanstack/react-query'
import { getPost } from '../api'

export function usePost(id: string | undefined) {
  return useQuery({
    queryKey: ['post', id],
    queryFn: () => getPost(id!),
    enabled: !!id,
  })
}
