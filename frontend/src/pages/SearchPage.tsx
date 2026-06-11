import { useState } from 'react'
import { useAuthStore } from '../shared/stores/authStore'
import { useSearch } from '../features/search/hooks/useSearch'
import { PostCard } from '../features/feed/components/PostCard'
import { TagFilter } from '../features/feed/components/TagFilter'
import { canEngage } from '../shared/lib/roles'
import { getErrorMessage } from '../shared/lib/errors'
import axios from 'axios'

export function SearchPage() {
  const [inputValue, setInputValue] = useState('')
  const [query, setQuery] = useState('')
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  const user = useAuthStore((s) => s.user)

  const { data, isPending, error, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useSearch({ q: query, tags: selectedTags })

  const handleToggleTag = (tagId: string) => {
    setSelectedTags((prev) =>
      prev.includes(tagId) ? prev.filter((id) => id !== tagId) : [...prev, tagId],
    )
  }

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    if (inputValue.trim()) {
      setQuery(inputValue.trim())
    }
  }

  return (
    <div className="space-y-4 max-w-3xl">
      <h1 className="text-2xl font-bold">Поиск</h1>
      
      <form onSubmit={handleSearch} className="flex gap-2 mb-6">
        <input 
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="Найти публикации..."
          className="flex-1 border border-gray-300 rounded px-3 py-2"
        />
        <button 
          type="submit"
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          disabled={!inputValue.trim()}
        >
          Найти
        </button>
      </form>

      {canEngage(user?.role) && query && <TagFilter selected={selectedTags} onToggle={handleToggleTag} />}

      {!query && (
        <p className="text-gray-500">Введите запрос для поиска публикаций.</p>
      )}

      {query && isPending && <p className="text-gray-500">Загрузка...</p>}

      {error && (
        <p className="text-red-500">
          {axios.isAxiosError(error) && error.response?.status === 403
            ? `Для просмотра нужна подписка уровня BASIC или выше. Текущая роль: ${user?.role ?? 'неизвестна'}.`
            : getErrorMessage(error)}
        </p>
      )}

      {!error && data && data.pages.every((p) => p.items.length === 0) && query && (
        <p className="text-gray-500">Ничего не найдено</p>
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
