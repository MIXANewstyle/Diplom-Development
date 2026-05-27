import { useInfiniteQuery } from '@tanstack/react-query'
import { getReplies } from '../api'

export function useReplies(commentId: string, options: { enabled: boolean }) {
  return useInfiniteQuery({
    queryKey: ['comment', commentId, 'replies'],
    enabled: options.enabled,
    queryFn: ({ pageParam }) => getReplies({ commentId, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
}
