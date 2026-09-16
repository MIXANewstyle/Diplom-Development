import { Link } from 'react-router-dom'
import type { DiaryCalendarDay } from '../types'
import { buildMonthGrid, isFutureLocally, isWritableLocally, todayIso } from '../lib/dates'

interface Props {
  month: string
  days: DiaryCalendarDay[]
}

const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']

export const MonthGrid = ({ month, days }: Props) => {
  const byDate = new Map(days.map((d) => [d.date, d]))
  const today = todayIso()
  const rows = buildMonthGrid(month)

  return (
    <div className="bg-white rounded-lg shadow p-3 md:p-4">
      <div className="grid grid-cols-7 text-center text-xs text-gray-500 mb-2">
        {WEEKDAYS.map((w) => (
          <div key={w} className="py-1">{w}</div>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-1">
        {rows.flat().map((cell) => {
          const entry = byDate.get(cell.iso)
          const isToday = cell.iso === today
          const future = isFutureLocally(cell.iso)
          const openable = !future && (!!entry || isWritableLocally(cell.iso))
          const base =
            'relative aspect-square rounded-lg flex flex-col items-center justify-center text-sm transition-colors'
          const tone = !cell.inMonth
            ? 'text-gray-300'
            : future
              ? 'text-gray-300'
              : 'text-gray-800'
          const ring = isToday ? 'ring-2 ring-blue-500 font-semibold' : ''
          const bg = entry ? 'bg-blue-50 hover:bg-blue-100' : openable ? 'hover:bg-gray-100' : ''

          const content = (
            <>
              <span>{cell.day}</span>
              {entry && (
                <span
                  className={`absolute bottom-1.5 w-1.5 h-1.5 rounded-full ${
                    entry.hasSummary ? 'bg-blue-600' : 'bg-blue-300'
                  }`}
                  title={entry.hasSummary ? 'Есть итог дня' : `Реплик: ${entry.turnCount}`}
                />
              )}
            </>
          )

          if (!openable) {
            return (
              <div key={cell.iso} className={`${base} ${tone} ${ring} cursor-default`}>
                {content}
              </div>
            )
          }
          return (
            <Link
              key={cell.iso}
              to={`/diary/${cell.iso}`}
              className={`${base} ${tone} ${ring} ${bg}`}
              aria-label={cell.iso}
            >
              {content}
            </Link>
          )
        })}
      </div>
      <div className="mt-3 flex items-center gap-4 text-xs text-gray-500">
        <span className="inline-flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-blue-300" /> запись</span>
        <span className="inline-flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-blue-600" /> есть итог дня</span>
        <span className="inline-flex items-center gap-1"><span className="w-3 h-3 rounded ring-2 ring-blue-500" /> сегодня</span>
      </div>
    </div>
  )
}
