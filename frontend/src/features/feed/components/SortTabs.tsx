import type { SortMode } from '../types'

const TABS: { value: SortMode; label: string }[] = [
  { value: 'newest', label: 'Новые' },
  { value: 'most_liked', label: 'Популярные' },
  { value: 'most_commented', label: 'Обсуждаемые' },
  { value: 'following', label: 'Подписки' },
]

export function SortTabs({
  value,
  onChange,
}: {
  value: SortMode
  onChange: (s: SortMode) => void
}) {
  return (
    <div className="flex gap-1 sm:gap-2 border-b pb-1 overflow-x-auto overscroll-x-contain [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      {TABS.map((tab) => (
        <button
          key={tab.value}
          type="button"
          onClick={() => onChange(tab.value)}
          className={`shrink-0 px-2.5 sm:px-3 py-1.5 text-sm font-medium transition-colors ${
            value === tab.value
              ? 'border-b-2 border-blue-600 text-blue-600'
              : 'text-gray-500 hover:text-gray-800'
          }`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}
