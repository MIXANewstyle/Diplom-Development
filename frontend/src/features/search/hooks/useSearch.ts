import { useInfiniteQuery } from '@tanstack/react-query'
import { searchPosts } from '../api'

export function useSearch(params: { q: string; tags: string[] }) {
  const tagsKey = params.tags.slice().sort().join(',')
  const query = params.q.trim()

  return useInfiniteQuery({
    queryKey: ['search', query, tagsKey],
    queryFn: ({ pageParam }) =>
      searchPosts({ q: query, cursor: pageParam, tagIds: params.tags }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    enabled: query.length > 0,
  })
}
