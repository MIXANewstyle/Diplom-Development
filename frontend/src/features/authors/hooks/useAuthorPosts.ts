import { useInfiniteQuery } from '@tanstack/react-query'
import { getAuthorPosts } from '../api'

export function useAuthorPosts(params: { authorId: string; tags: string[] }) {
  const tagsKey = params.tags.slice().sort().join(',')

  return useInfiniteQuery({
    queryKey: ['author', params.authorId, tagsKey],
    queryFn: ({ pageParam }) =>
      getAuthorPosts({ authorId: params.authorId, cursor: pageParam, tagIds: params.tags }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
}
