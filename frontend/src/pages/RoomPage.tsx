import { useParams } from 'react-router-dom'
import { useRoom } from '../features/chat/hooks/useRoom'
import { useTurns } from '../features/chat/hooks/useTurns'
import { useSubmitTurn } from '../features/chat/hooks/useSubmitTurn'
import { useEndSoloRoom } from '../features/chat/hooks/useEndSoloRoom'
import { Transcript } from '../features/chat/components/Transcript'
import { Composer } from '../features/chat/components/Composer'
import { PairedRoomView } from '../features/chat/components/PairedRoomView'
import { useEffect, useRef, useState } from 'react'
import { useRoomSocket } from '../shared/ws/useRoomSocket'
import { useQueryClient, useMutation } from '@tanstack/react-query'
import { Pencil, Check, X } from 'lucide-react'
import { renameRoom } from '../features/chat/api'
import { getErrorMessage } from '../shared/lib/errors'

export const RoomPage = () => {
  const { roomId } = useParams<{ roomId: string }>()
  const id = roomId || ''

  const { data: room, isLoading: isRoomLoading, isError: isRoomError } = useRoom(id)
  
  if (isRoomError) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 text-center">
        <h1 className="text-2xl font-bold text-gray-800 mb-4">Комната не найдена</h1>
        <p className="text-gray-600">Возможно, она была удалена или у вас нет к ней доступа.</p>
      </div>
    )
  }

  if (isRoomLoading || !room) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 text-center text-gray-500">
        Загрузка сессии...
      </div>
    )
  }

  if (room.type === 'PAIRED') {
    return <PairedRoomView roomId={id} />
  }

  return <SoloRoomView id={id} room={room} />
}

const SoloRoomView = ({ id, room }: { id: string, room: any }) => {
  const { data: turnsPage, isLoading: isTurnsLoading } = useTurns(id)
  
  const submitTurnMutation = useSubmitTurn(id)
  const endRoomMutation = useEndSoloRoom(id)

  const transcriptContainerRef = useRef<HTMLDivElement>(null)
  const { status: wsStatus, snapshot, error: wsError } = useRoomSocket(id)
  const queryClient = useQueryClient()

  // Rename inline editing
  const [isEditingTitle, setIsEditingTitle] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const renameMutation = useMutation({
    mutationFn: (newTitle: string | null) => renameRoom(id, newTitle),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'room', id] })
      queryClient.invalidateQueries({ queryKey: ['chat', 'rooms'] })
      setIsEditingTitle(false)
    },
    onError: (err) => alert('Ошибка: ' + getErrorMessage(err)),
  })
  const startRename = () => { setEditTitle(room?.title || ''); setIsEditingTitle(true) }
  const submitRename = () => { renameMutation.mutate(editTitle.trim() || null) }
  const cancelRename = () => setIsEditingTitle(false)

  useEffect(() => {
    if (transcriptContainerRef.current) {
      transcriptContainerRef.current.scrollTop = transcriptContainerRef.current.scrollHeight
    }
  }, [turnsPage?.items])

  if (isTurnsLoading) {
    return <div className="text-center py-4 text-gray-500">Загрузка сообщений...</div>
  }

  const isActive = room.status === 'ACTIVE'
  const isPending = submitTurnMutation.isPending

  const handleEndSession = () => {
    if (window.confirm('Вы уверены, что хотите завершить эту сессию?')) {
      endRoomMutation.mutate()
    }
  }

  return (
    <div className="max-w-4xl mx-auto h-[calc(100vh-80px)] flex flex-col py-4 px-2 md:px-4">
      {/* Header */}
      <div className="flex justify-between items-start gap-2 pb-4 border-b shrink-0">
        <div className="min-w-0">
          {isEditingTitle ? (
            <div className="flex items-center gap-1.5">
              <input
                type="text"
                value={editTitle}
                onChange={(e) => setEditTitle(e.target.value)}
                maxLength={100}
                autoFocus
                onKeyDown={(e) => { if (e.key === 'Enter') submitRename(); if (e.key === 'Escape') cancelRename(); }}
                className="text-xl font-bold text-gray-900 border-b-2 border-blue-500 outline-none bg-transparent w-full min-w-0"
                placeholder="Название сессии"
              />
              <button onClick={submitRename} disabled={renameMutation.isPending} className="p-1 text-green-600 hover:text-green-800 shrink-0"><Check size={18} /></button>
              <button onClick={cancelRename} className="p-1 text-gray-400 hover:text-gray-600 shrink-0"><X size={18} /></button>
            </div>
          ) : (
            <div className="flex items-center gap-1.5 group/title">
              <h1 className="text-xl font-bold text-gray-900">{room?.title || 'Соло сессия'}</h1>
              <button onClick={startRename} className="p-1 text-gray-400 hover:text-blue-600 opacity-0 group-hover/title:opacity-100 focus:opacity-100 transition-opacity shrink-0" title="Переименовать"><Pencil size={14} /></button>
            </div>
          )}
          <div className="text-sm text-gray-500">Статус: {room.status}</div>
        </div>
        <div className="flex flex-col items-end gap-1 shrink-0">
          <div className="text-xs text-gray-500">WS: {wsStatus}</div>
          {wsError && <div className="text-xs text-red-500 break-all">WS Error: {wsError}</div>}
          {snapshot && <div className="text-xs text-gray-500">Turns: {snapshot.recentTurns?.length || 0}</div>}
          {isActive && (
            <button
              onClick={handleEndSession}
              disabled={endRoomMutation.isPending}
              className="mt-1 px-3 py-1.5 text-sm font-medium text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors whitespace-nowrap"
            >
              {endRoomMutation.isPending ? 'Завершение...' : 'Завершить'}
            </button>
          )}
        </div>
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
