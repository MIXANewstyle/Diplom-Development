import { useParams } from 'react-router-dom'
import { useAuthStore } from '../shared/stores/authStore'
import { useAuthorPosts } from '../features/authors/hooks/useAuthorPosts'
import { PostCard } from '../features/feed/components/PostCard'
import type { AxiosError } from 'axios'

export function AuthorPage() {
  const { authorId } = useParams<{ authorId: string }>()
  const user = useAuthStore((s) => s.user)

  // Minimal implementation without tags filter for the author page
  const { data, isPending, error, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useAuthorPosts({ authorId: authorId || '', tags: [] })

  const axiosError = error as AxiosError<{ message?: string }> | null
  
  // Extract author info from the first post if available
  const firstPost = data?.pages[0]?.items[0]
  const authorName = firstPost?.authorUsername || `Автор ${authorId?.substring(0, 8) || ''}`
  const authorAvatarUrl = firstPost?.authorAvatarUrl

  return (
    <div className="space-y-4 max-w-3xl">
      <div className="flex items-center gap-4 mb-6 pb-6 border-b border-gray-200">
        {authorAvatarUrl ? (
          <img 
            src={authorAvatarUrl} 
            alt={authorName} 
            className="w-16 h-16 rounded-full object-cover border border-gray-200"
          />
        ) : (
          <div className="w-16 h-16 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-bold text-xl">
            {authorName.charAt(0).toUpperCase()}
          </div>
        )}
        <h1 className="text-2xl font-bold">{authorName}</h1>
      </div>

      {isPending && <p className="text-gray-500">Загрузка...</p>}

      {axiosError && (
        <p className="text-red-500">
          {axiosError.response?.status === 403
            ? `Для просмотра нужна подписка уровня BASIC или выше. Текущая роль: ${user?.role ?? 'неизвестна'}.`
            : axiosError.response?.data?.message ?? axiosError.message}
        </p>
      )}

      {!error && data && data.pages.every((p) => p.items.length === 0) && (
        <p className="text-gray-500">Публикаций пока нет</p>
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
