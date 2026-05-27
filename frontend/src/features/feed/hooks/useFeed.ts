import { useInfiniteQuery } from '@tanstack/react-query'
import { getFeed } from '../api'
import type { SortMode } from '../types'

export function useFeed(params: { sort: SortMode; tags: string[] }) {
  const tagsKey = params.tags.slice().sort().join(',')

  return useInfiniteQuery({
    queryKey: ['feed', params.sort, tagsKey],
    queryFn: ({ pageParam }) =>
      getFeed({ sort: params.sort, cursor: pageParam, tags: params.tags }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
}
