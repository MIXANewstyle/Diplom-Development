import { useEffect, useRef } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight, CheckCircle2, Loader2 } from 'lucide-react'
import { isSummaryPending, useCloseDay, useDiaryDay, useDiaryMemories } from '../features/diary/hooks/useDiary'
import { MemoryCard } from '../features/diary/components/MemoryCard'
import { addDays, formatDayTitle, isFutureLocally, isWritableLocally, parseIsoDate, todayIso } from '../features/diary/lib/dates'
import { useRoom } from '../features/chat/hooks/useRoom'
import { useTurns } from '../features/chat/hooks/useTurns'
import { useSubmitTurn } from '../features/chat/hooks/useSubmitTurn'
import { DialogueTranscript } from '../features/chat/components/DialogueTranscript'
import { Composer } from '../features/chat/components/Composer'
import { getErrorMessage } from '../shared/lib/errors'

export const DiaryDayPage = () => {
  const { date = '' } = useParams<{ date: string }>()
  const valid = parseIsoDate(date) !== null && !isFutureLocally(date)

  const { data: day, isLoading, isError, error } = useDiaryDay(date, valid)
  const { data: memories } = useDiaryMemories(date, valid)

  if (!valid) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 text-center">
        <h1 className="text-2xl font-bold text-gray-800 mb-2">Такого дня нет</h1>
        <Link to="/diary" className="text-blue-600 hover:underline">Вернуться к календарю</Link>
      </div>
    )
  }

  const nextDisabled = isFutureLocally(addDays(date, 1))

  return (
    <div className="max-w-4xl mx-auto h-[calc(100vh-80px)] flex flex-col py-4 px-2 md:px-4">
      <div className="flex items-center justify-between gap-2 pb-3 border-b shrink-0">
        <Link to={`/diary/${addDays(date, -1)}`} className="p-2 rounded hover:bg-gray-100" aria-label="Предыдущий день">
          <ChevronLeft size={20} />
        </Link>
        <div className="text-center min-w-0">
          <Link to="/diary" className="text-xs text-gray-400 hover:text-gray-600">Дневник</Link>
          <h1 className="text-lg md:text-xl font-bold text-gray-900 truncate">{formatDayTitle(date)}</h1>
          {day && !day.writable && !isSummaryPending(day) && (
            <div className="text-xs text-gray-500">День закрыт — только чтение</div>
          )}
          {day && isSummaryPending(day) && (
            <div className="text-xs text-blue-600 inline-flex items-center gap-1">
              <Loader2 size={12} className="animate-spin" /> Подвожу итог дня…
            </div>
          )}
        </div>
        {nextDisabled ? (
          <span className="p-2 text-gray-300" aria-hidden><ChevronRight size={20} /></span>
        ) : (
          <Link to={`/diary/${addDays(date, 1)}`} className="p-2 rounded hover:bg-gray-100" aria-label="Следующий день">
            <ChevronRight size={20} />
          </Link>
        )}
      </div>

      {isLoading && <div className="text-center py-8 text-gray-500">Открываю день…</div>}
      {isError && <div className="text-center py-8 text-red-600">{getErrorMessage(error)}</div>}

      {!isLoading && !isError && day === null && (
        <div className="flex-1 flex flex-col items-center justify-center text-center gap-3 text-gray-500">
          <p>В этот день записей нет.</p>
          {!isWritableLocally(date) && <p className="text-sm">Писать можно только за сегодня и вчера.</p>}
          <Link to={`/diary/${todayIso()}`} className="text-blue-600 hover:underline">Написать сегодня</Link>
        </div>
      )}

      {day && (
        <DayConversation
          date={date}
          roomId={day.roomId}
          writable={day.writable}
          summary={day.summary}
          summaryPending={isSummaryPending(day)}
          memories={memories?.items ?? []}
        />
      )}
    </div>
  )
}

const DayConversation = ({
  date,
  roomId,
  writable,
  summary,
  summaryPending,
  memories,
}: {
  date: string
  roomId: string
  writable: boolean
  summary: string | null
  summaryPending: boolean
  memories: Parameters<typeof MemoryCard>[0]['items']
}) => {
  const { data: room } = useRoom(roomId)
  const { data: turnsPage, isLoading } = useTurns(roomId)
  const myParticipantId = room?.participants?.[0]?.id
  const submitTurn = useSubmitTurn(roomId, myParticipantId)
  const closeDay = useCloseDay(date)
  const transcriptRef = useRef<HTMLDivElement>(null)
  const hasTurns = (turnsPage?.items?.length ?? 0) > 0

  const handleClose = () => {
    if (!window.confirm('Подвести итог дня? После этого дописать в этот день будет нельзя.')) return
    closeDay.mutate(undefined, { onError: (e) => alert(getErrorMessage(e)) })
  }

  useEffect(() => {
    if (transcriptRef.current) {
      transcriptRef.current.scrollTop = transcriptRef.current.scrollHeight
    }
  }, [turnsPage?.items])

  return (
    <>
      <div ref={transcriptRef} className="flex-1 overflow-y-auto bg-gray-50 rounded-t-lg">
        {(memories.length > 0 || summary || summaryPending) && (
          <div className="p-3 space-y-3">
            <MemoryCard items={memories} />
            {summary && (
              <div className="rounded-lg border border-gray-200 bg-white p-3">
                <div className="text-xs font-medium text-gray-500 mb-1 inline-flex items-center gap-1">
                  <CheckCircle2 size={12} className="text-green-600" /> Итог дня
                </div>
                <div className="text-sm text-gray-800 whitespace-pre-wrap">{summary}</div>
              </div>
            )}
            {!summary && summaryPending && (
              <div className="rounded-lg border border-blue-100 bg-blue-50 p-3 text-sm text-blue-800 inline-flex items-center gap-2">
                <Loader2 size={14} className="animate-spin" />
                Итог дня формируется: собеседник перечитывает запись, выделяет главное и запоминает устойчивое. Обычно это занимает меньше минуты.
              </div>
            )}
          </div>
        )}
        {isLoading ? (
          <div className="text-center py-4 text-gray-500">Загрузка записи…</div>
        ) : (
          <DialogueTranscript
            historyTurns={turnsPage?.items || []}
            liveTurns={[]}
            otherDrafts={{}}
            aiThinking={submitTurn.isPending}
            participants={room?.participants || []}
            myParticipantId={myParticipantId}
          />
        )}
      </div>
      <div className="shrink-0">
        {writable ? (
          <>
            {hasTurns && (
              <div className="px-3 md:px-4 pt-2 bg-white border-t flex justify-end">
                <button
                  onClick={handleClose}
                  disabled={closeDay.isPending || submitTurn.isPending}
                  className="text-sm px-3 py-1.5 rounded-lg text-blue-700 bg-blue-50 hover:bg-blue-100 disabled:opacity-50 transition-colors"
                  title="Закрыть день: собеседник подведёт итог и запомнит главное"
                >
                  {closeDay.isPending ? 'Закрываю…' : 'Подвести итог дня'}
                </button>
              </div>
            )}
            <Composer
              isActive
              isPending={submitTurn.isPending}
              onSubmit={(text, restoreText) => {
                submitTurn.mutate(text, { onError: () => restoreText() })
              }}
            />
          </>
        ) : (
          <div className="p-4 bg-gray-50 border-t text-center text-gray-500 text-sm">
            {summary || summaryPending
              ? 'День закрыт. Итог и всё важное из него собеседник будет помнить дальше.'
              : 'Этот день закрыт. Писать можно только за сегодня и вчера.'}
          </div>
        )}
      </div>
    </>
  )
}
