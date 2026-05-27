import { useInfiniteQuery } from '@tanstack/react-query'
import { getRootComments } from '../api'

export function useRootComments(postId: string | undefined) {
  return useInfiniteQuery({
    queryKey: ['post', postId, 'comments'],
    enabled: !!postId,
    queryFn: ({ pageParam }) => getRootComments({ postId: postId!, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
}
