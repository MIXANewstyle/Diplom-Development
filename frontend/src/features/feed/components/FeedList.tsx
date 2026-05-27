import { useState } from 'react'
import { useAuthStore } from '../../../shared/stores/authStore'
import { useFeed } from '../hooks/useFeed'
import { PostCard } from './PostCard'
import { SortTabs } from './SortTabs'
import { TagFilter } from './TagFilter'
import type { SortMode } from '../types'
import type { AxiosError } from 'axios'

export function FeedList() {
  const [sort, setSort] = useState<SortMode>('newest')
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  const user = useAuthStore((s) => s.user)

  const { data, isPending, error, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useFeed({ sort, tags: selectedTags })

  const handleToggleTag = (tagId: string) => {
    setSelectedTags((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    )
  }

  const axiosError = error as AxiosError<{ message?: string }> | null

  return (
    <div className="space-y-4">
      <SortTabs value={sort} onChange={setSort} />
      <TagFilter selected={selectedTags} onToggle={handleToggleTag} />

      {isPending && <p className="text-gray-500">Загрузка...</p>}

      {axiosError && (
        <p className="text-red-500">
          {axiosError.response?.status === 403
            ? `Для просмотра ленты нужна подписка уровня BASIC или выше. Текущая роль: ${user?.role ?? 'неизвестна'}.`
            : axiosError.response?.data?.message ?? axiosError.message}
        </p>
      )}

      {!error && data && data.pages.every((p) => p.items.length === 0) && (
        <p className="text-gray-500">Постов пока нет</p>
      )}

      {!error &&
        data?.pages.map((page, pageIndex) =>
          page.items.map((post) => (
            <PostCard key={`${pageIndex}-${post.id}`} post={post} />
          )),
        )}

      {!error && hasNextPage && (
        <button
          type="button"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="w-full py-2 text-sm font-medium text-blue-600 border border-blue-600 rounded hover:bg-blue-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isFetchingNextPage ? 'Загрузка...' : 'Загрузить ещё'}
        </button>
      )}
    </div>
  )
}
