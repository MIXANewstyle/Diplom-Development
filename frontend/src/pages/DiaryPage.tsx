import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useDiaryCalendar, useGenerateAutoSummary, useSaveUserSummary } from '../features/diary/hooks/useDiary'
import { MonthGrid } from '../features/diary/components/MonthGrid'
import { PeriodSummaryPanel } from '../features/diary/components/PeriodSummaryPanel'
import { MemoryFactsPanel } from '../features/diary/components/MemoryFactsPanel'
import type { DiaryPeriodResponse } from '../features/diary/types'
import { addMonths, formatMonthTitle, formatRange, monthKey, todayIso } from '../features/diary/lib/dates'
import { getErrorMessage } from '../shared/lib/errors'

export const DiaryPage = () => {
  const today = todayIso()
  const [month, setMonth] = useState(monthKey(today))
  const [selected, setSelected] = useState<{ type: 'WEEK' | 'MONTH'; start: string } | null>(null)

  const { data, isLoading, isError, error } = useDiaryCalendar(month)
  const generate = useGenerateAutoSummary(month)
  const saveUser = useSaveUserSummary(month)

  const selectedPeriod: DiaryPeriodResponse | null = (() => {
    if (!data || !selected) return null
    if (selected.type === 'MONTH') return data.monthPeriod
    return data.weeks.find((w) => w.start === selected.start) ?? null
  })()

  const onGenerate = (p: DiaryPeriodResponse) =>
    generate.mutate({ type: p.type, start: p.start }, { onError: (e) => alert(getErrorMessage(e)) })
  const onSaveUser = (p: DiaryPeriodResponse, text: string) =>
    saveUser.mutate({ type: p.type, start: p.start, text }, { onError: (e) => alert(getErrorMessage(e)) })

  const isCurrentMonth = month === monthKey(today)

  return (
    <div className="max-w-5xl mx-auto py-4 md:py-8 px-2 md:px-4 space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Дневник</h1>
          <p className="text-sm text-gray-500">Каждый день — разговор. Собеседник помнит, что было раньше.</p>
        </div>
        <Link
          to={`/diary/${today}`}
          className="px-5 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors text-center"
        >
          Написать сегодня
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_20rem] gap-6 items-start">
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <button
              onClick={() => { setMonth(addMonths(month, -1)); setSelected(null) }}
              className="p-2 rounded hover:bg-gray-100"
              aria-label="Предыдущий месяц"
            >
              <ChevronLeft size={20} />
            </button>
            <h2 className="text-lg font-semibold text-gray-800">{formatMonthTitle(month)}</h2>
            <button
              onClick={() => { setMonth(addMonths(month, 1)); setSelected(null) }}
              disabled={isCurrentMonth}
              className="p-2 rounded hover:bg-gray-100 disabled:opacity-30"
              aria-label="Следующий месяц"
            >
              <ChevronRight size={20} />
            </button>
          </div>

          {isLoading && <div className="text-center text-gray-500 py-8">Загрузка календаря…</div>}
          {isError && <div className="text-center text-red-600 py-8">{getErrorMessage(error)}</div>}
          {data && <MonthGrid month={month} days={data.days} />}

          {data && (
            <div className="bg-white rounded-lg shadow divide-y">
              <button
                onClick={() => setSelected({ type: 'MONTH', start: data.monthPeriod.start })}
                className={`w-full text-left px-4 py-3 flex items-center justify-between hover:bg-gray-50 ${
                  selected?.type === 'MONTH' ? 'bg-blue-50' : ''
                }`}
              >
                <span className="font-medium text-gray-800">Итог месяца</span>
                <PeriodBadges p={data.monthPeriod} />
              </button>
              {data.weeks.map((w) => (
                <button
                  key={w.start}
                  onClick={() => setSelected({ type: 'WEEK', start: w.start })}
                  className={`w-full text-left px-4 py-2.5 flex items-center justify-between hover:bg-gray-50 ${
                    selected?.type === 'WEEK' && selected.start === w.start ? 'bg-blue-50' : ''
                  }`}
                >
                  <span className="text-sm text-gray-700">Неделя {formatRange(w.start, w.end)}</span>
                  <PeriodBadges p={w} />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="space-y-4">
          {selectedPeriod && (
            <PeriodSummaryPanel
              key={`${selectedPeriod.type}-${selectedPeriod.start}`}
              period={selectedPeriod}
              onGenerate={onGenerate}
              onSaveUser={onSaveUser}
              generating={generate.isPending}
              saving={saveUser.isPending}
              onClose={() => setSelected(null)}
            />
          )}
          <MemoryFactsPanel />
        </div>
      </div>
    </div>
  )
}

const PeriodBadges = ({ p }: { p: DiaryPeriodResponse }) => (
  <span className="flex items-center gap-1.5 text-xs">
    {p.autoSummary && <span className="px-1.5 py-0.5 rounded bg-blue-100 text-blue-700">итог</span>}
    {p.userSummary && <span className="px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">своими словами</span>}
    {!p.autoSummary && !p.userSummary && <span className="text-gray-400">—</span>}
  </span>
)
