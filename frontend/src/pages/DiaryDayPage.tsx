import { useEffect, useRef } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useDiaryDay, useDiaryMemories } from '../features/diary/hooks/useDiary'
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
          {day && !day.writable && (
            <div className="text-xs text-gray-500">День закрыт — только чтение</div>
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
        <DayConversation roomId={day.roomId} writable={day.writable} summary={day.summary} memories={memories?.items ?? []} />
      )}
    </div>
  )
}

const DayConversation = ({
  roomId,
  writable,
  summary,
  memories,
}: {
  roomId: string
  writable: boolean
  summary: string | null
  memories: Parameters<typeof MemoryCard>[0]['items']
}) => {
  const { data: room } = useRoom(roomId)
  const { data: turnsPage, isLoading } = useTurns(roomId)
  const myParticipantId = room?.participants?.[0]?.id
  const submitTurn = useSubmitTurn(roomId, myParticipantId)
  const transcriptRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (transcriptRef.current) {
      transcriptRef.current.scrollTop = transcriptRef.current.scrollHeight
    }
  }, [turnsPage?.items])

  return (
    <>
      <div ref={transcriptRef} className="flex-1 overflow-y-auto bg-gray-50 rounded-t-lg">
        {(memories.length > 0 || (!writable && summary)) && (
          <div className="p-3 space-y-3">
            <MemoryCard items={memories} />
            {!writable && summary && (
              <div className="rounded-lg border border-gray-200 bg-white p-3">
                <div className="text-xs font-medium text-gray-500 mb-1">Итог дня</div>
                <div className="text-sm text-gray-800 whitespace-pre-wrap">{summary}</div>
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
          <Composer
            isActive
            isPending={submitTurn.isPending}
            onSubmit={(text, restoreText) => {
              submitTurn.mutate(text, { onError: () => restoreText() })
            }}
          />
        ) : (
          <div className="p-4 bg-gray-50 border-t text-center text-gray-500 text-sm">
            Этот день закрыт. Писать можно только за сегодня и вчера.
          </div>
        )}
      </div>
    </>
  )
}
