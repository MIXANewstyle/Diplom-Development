import { useTags } from '../hooks/useTags'

export function TagFilter({
  selected,
  onToggle,
}: {
  selected: string[]
  onToggle: (tagId: string) => void
}) {
  const { data, isPending, isError } = useTags()

  if (isPending) return <p className="text-sm text-gray-400">Загрузка тегов...</p>
  if (isError) return <p className="text-sm text-red-500">Не удалось загрузить теги</p>

  const tags = data.content

  if (tags.length === 0) return null

  return (
    <div>
      <p className="text-xs text-gray-400 mb-1">Фильтр по тегам (можно несколько):</p>
      <div className="flex flex-wrap gap-1">
        {tags.map((tag) => {
          const isSelected = selected.includes(tag.id)
          return (
            <button
              key={tag.id}
              type="button"
              onClick={() => onToggle(tag.id)}
              className={`text-xs rounded px-2 py-1 transition-colors ${
                isSelected
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
              }`}
            >
              {tag.name}
            </button>
          )
        })}
      </div>
    </div>
  )
}
