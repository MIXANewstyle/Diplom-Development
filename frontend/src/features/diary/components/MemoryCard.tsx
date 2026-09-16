import { Link } from 'react-router-dom'
import type { DiaryMemory } from '../types'
import { formatLong } from '../lib/dates'

interface Props {
  items: DiaryMemory[]
}

const KIND_LABEL: Record<DiaryMemory['kind'], string> = {
  YEAR_AGO: 'В этот день год назад',
  MONTH_AGO: 'В этот день месяц назад',
}

export const MemoryCard = ({ items }: Props) => {
  if (items.length === 0) return null
  return (
    <div className="space-y-2">
      {items.map((m) => (
        <Link
          key={m.kind}
          to={`/diary/${m.date}`}
          className="block rounded-lg border border-amber-200 bg-amber-50 p-3 hover:bg-amber-100 transition-colors"
        >
          <div className="text-xs font-medium text-amber-700">
            {KIND_LABEL[m.kind]} · {formatLong(m.date)}
          </div>
          <div className="text-sm text-gray-800 mt-1 line-clamp-3 whitespace-pre-wrap">
            {m.summary ? m.summary : `Запись без итога · реплик: ${m.turnCount}`}
          </div>
        </Link>
      ))}
    </div>
  )
}
