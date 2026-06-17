import { useQuery } from '@tanstack/react-query'
import { getAuthorProfile } from '../api'

export function useAuthorProfile(authorId: string) {
  return useQuery({
    queryKey: ['users', 'profile', authorId],
    queryFn: () => getAuthorProfile(authorId),
    enabled: !!authorId,
    retry: (failureCount, error: unknown) => {
      // Don't retry on 404 — author simply doesn't exist
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 404) return false
      return failureCount < 2
    },
  })
}
