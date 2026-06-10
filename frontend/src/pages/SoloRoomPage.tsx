import { useParams } from 'react-router-dom'
import { useRoom } from '../features/chat/hooks/useRoom'
import { useTurns } from '../features/chat/hooks/useTurns'
import { useSubmitTurn } from '../features/chat/hooks/useSubmitTurn'
import { useEndSoloRoom } from '../features/chat/hooks/useEndSoloRoom'
import { Transcript } from '../features/chat/components/Transcript'
import { Composer } from '../features/chat/components/Composer'
import { useEffect, useRef } from 'react'
import { useRoomSocket } from '../shared/ws/useRoomSocket'

export const SoloRoomPage = () => {
  const { roomId } = useParams<{ roomId: string }>()
  const id = roomId || ''

  const { data: room, isLoading: isRoomLoading, isError: isRoomError } = useRoom(id)
  const { data: turnsPage, isLoading: isTurnsLoading } = useTurns(id)
  
  const submitTurnMutation = useSubmitTurn(id)
  const endRoomMutation = useEndSoloRoom(id)

  const transcriptContainerRef = useRef<HTMLDivElement>(null)

  const { status: wsStatus, snapshot, error: wsError } = useRoomSocket(id)

  useEffect(() => {
    // Scroll to bottom when turns change
    if (transcriptContainerRef.current) {
      transcriptContainerRef.current.scrollTop = transcriptContainerRef.current.scrollHeight
    }
  }, [turnsPage?.items])

  if (isRoomError) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 text-center">
        <h1 className="text-2xl font-bold text-gray-800 mb-4">Комната не найдена</h1>
        <p className="text-gray-600">Возможно, она была удалена или у вас нет к ней доступа.</p>
      </div>
    )
  }

  if (isRoomLoading || isTurnsLoading || !room) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 text-center text-gray-500">
        Загрузка сессии...
      </div>
    )
  }

  const isActive = room.status === 'ACTIVE'
  const isPending = submitTurnMutation.isPending

  const handleEndSession = () => {
    if (window.confirm('Вы уверены, что хотите завершить эту сессию?')) {
      endRoomMutation.mutate()
    }
  }

  return (
    <div className="max-w-4xl mx-auto h-[calc(100vh-80px)] flex flex-col py-4 px-4">
      {/* Header */}
      <div className="flex justify-between items-center pb-4 border-b shrink-0">
        <div>
          <h1 className="text-xl font-bold text-gray-900">
            {room.type === 'SOLO' ? 'Соло сессия' : 'Парная сессия'}
          </h1>
          <div className="text-sm text-gray-500">Статус: {room.status}</div>
        </div>
        <div className="flex flex-col items-end text-xs text-gray-500 mr-4">
          <div>WS: {wsStatus}</div>
          {wsError && <div className="text-red-500">WS Error: {wsError}</div>}
          {snapshot && <div>Snapshot received: {snapshot.recentTurns.length} turns</div>}
        </div>
        {isActive && (
          <button
            onClick={handleEndSession}
            disabled={endRoomMutation.isPending}
            className="px-4 py-2 text-sm font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors"
          >
            {endRoomMutation.isPending ? 'Завершение...' : 'Завершить сессию'}
          </button>
        )}
      </div>

      {/* Transcript Area */}
      <div
        ref={transcriptContainerRef}
        className="flex-1 overflow-y-auto bg-gray-50 p-4 rounded-b-none rounded-t-lg"
      >
        <Transcript turns={turnsPage?.items || []} />
      </div>

      {/* Composer Area */}
      <div className="shrink-0">
        <Composer
          isActive={isActive}
          isPending={isPending}
          onSubmit={(text) => submitTurnMutation.mutate(text)}
        />
      </div>
    </div>
  )
}
