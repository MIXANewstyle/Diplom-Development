import { useEffect } from 'react'
import { useRoom } from '../hooks/useRoom'
import { useJoinRoom } from '../hooks/useJoinRoom'
import { useRoomSocket } from '../../../shared/ws/useRoomSocket'
import { useAuthStore } from '../../../shared/stores/authStore'
import { useQueryClient } from '@tanstack/react-query'
import { useTurns } from '../hooks/useTurns'
import { DialogueTranscript } from './DialogueTranscript'
import { PairedComposer } from './PairedComposer'

interface Props {
  roomId: string
}

export const PairedRoomView = ({ roomId }: Props) => {
  const me = useAuthStore((s) => s.user)
  const myId = me?.id

  const { data: room, isLoading: isRoomLoading, isError: isRoomError } = useRoom(roomId)
  const joinRoomMutation = useJoinRoom(roomId)

  const myParticipantId = room?.participants?.find((p) => p.userId === myId)?.id

  const {
    status: wsStatus,
    error: wsError,
    lastEvent,
    consentByParticipant,
    onlineParticipants,
    dialogueStarted,
    currentFloorParticipantId,
    maxSeq,
    setMaxSeq,
    liveTurns,
    aiThinking,
    otherDrafts,
    endProposerParticipantId,
    archived,
    sendConsentStart,
    sendConsentRevoke,
    upsertDraft,
    proposeEnd,
    agreeEnd,
    declineEnd,
    finishThought,
  } = useRoomSocket(roomId, myParticipantId)

  const { data: turnsPage } = useTurns(roomId)

  // Seed maxSeq from history if not set
  useEffect(() => {
    if (turnsPage?.items && maxSeq === 0) {
      const highest = turnsPage.items.reduce((max, t) => Math.max(max, t.seq), 0)
      if (highest > 0) setMaxSeq(highest)
    }
  }, [turnsPage?.items, maxSeq, setMaxSeq])

  const queryClient = useQueryClient()

  // Sync REST state when room membership, consent, or messages change over WS
  useEffect(() => {
    const eventType = (lastEvent as { type?: string } | null)?.type
    if (
      eventType === 'PARTICIPANT_JOINED' ||
      eventType === 'CONSENT_UPDATED' ||
      eventType === 'DIALOGUE_STARTED'
    ) {
      queryClient.invalidateQueries({ queryKey: ['chat', 'room', roomId] })
    }
    if (eventType === 'AI_THINKING' || eventType === 'AI_RESPONSE' || eventType === 'DIALOGUE_STARTED') {
      queryClient.invalidateQueries({ queryKey: ['chat', 'turns', roomId] })
    }
  }, [lastEvent, queryClient, roomId])

  if (isRoomError) {
    return (
      <div className="max-w-4xl mx-auto py-8 px-4 text-center">
        <h1 className="text-2xl font-bold text-gray-800 mb-4">Комната не найдена</h1>
      </div>
    )
  }

  if (isRoomLoading || !room) {
    return <div className="max-w-4xl mx-auto py-8 px-4 text-center text-gray-500">Загрузка сессии...</div>
  }

  const participants = room.participants || []
  const myParticipant = participants.find((p) => p.userId === myId)
  
  // Am I the invitee who hasn't joined yet?
  const amIInvitee = myParticipant && myParticipant.joinedAt === null
  const amIHost = room.ownerUserId === myId
  
  // Current user's consent state
  const haveIConsented = myParticipantId ? !!consentByParticipant[myParticipantId] || !!myParticipant?.consentStartAt : false

  const handleJoin = () => {
    joinRoomMutation.mutate()
  }

  const statusDisplay = room.status === 'CREATED' 
    ? 'Ожидание подключения' 
    : room.status === 'WAITING_CONSENT'
    ? 'Ожидание готовности'
    : room.status === 'ACTIVE' || dialogueStarted
    ? 'Активна'
    : room.status

  return (
    <div className="max-w-4xl mx-auto h-[calc(100vh-80px)] flex flex-col py-4 px-4 gap-6">
      <div className="flex justify-between items-center pb-4 border-b shrink-0">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Парная сессия</h1>
          <div className="text-sm text-gray-500">Статус: {statusDisplay}</div>
        </div>
        <div className="flex flex-col items-end text-xs text-gray-500 mr-4">
          <div>WS: {wsStatus}</div>
          {wsError && <div className="text-red-500">WS Error: {wsError}</div>}
        </div>
      </div>

      <div className="flex flex-col md:flex-row gap-6">
        {participants.map((p) => {
          const isMe = p.userId === myId
          const hasConsented = !!consentByParticipant[p.id] || !!p.consentStartAt
          const isFloorHolder = currentFloorParticipantId === p.id
          const hasJoined = p.joinedAt !== null
          const isOnline = hasJoined && (
            (isMe && wsStatus === 'connected') || onlineParticipants.has(p.id)
          )

          return (
            <div key={p.id} className="flex-1 bg-white border border-gray-200 rounded-lg p-4 shadow-sm flex items-center gap-4 relative">
              {p.avatarUrl ? (
                <img src={p.avatarUrl} alt={p.displayName} className="w-12 h-12 rounded-full object-cover" />
              ) : (
                <div className="w-12 h-12 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-bold text-lg">
                  {p.displayName?.charAt(0).toUpperCase() || '?'}
                </div>
              )}
              <div className="flex-1">
                <div className="font-bold text-gray-900">
                  {p.displayName} {isMe && <span className="text-gray-400 font-normal text-sm">(Вы)</span>}
                </div>
                <div className="text-xs text-gray-500">{p.role}</div>
              </div>
              <div className="flex flex-col items-end gap-1">
                {hasConsented && <span className="text-green-600 text-sm font-medium">✓ готов(а)</span>}
                {isFloorHolder && <span className="text-blue-600 text-xs font-bold bg-blue-50 px-2 py-0.5 rounded">Говорит</span>}
                {!hasJoined && <span className="text-gray-400 text-sm">Не присоединился</span>}
                {hasJoined && isOnline && <span className="text-green-600 text-sm">В сети</span>}
                {hasJoined && !isOnline && <span className="text-gray-400 text-sm">Не в сети</span>}
              </div>
            </div>
          )
        })}
      </div>

      <div className="bg-gray-50 border border-gray-200 rounded-lg p-8 text-center flex-1 flex flex-col items-center justify-center">
        {room.status === 'CREATED' && (
          <div className="space-y-4">
            {amIInvitee ? (
              <>
                <p className="text-gray-700">Вас пригласили в эту комнату.</p>
                <button
                  onClick={handleJoin}
                  disabled={joinRoomMutation.isPending}
                  className="px-6 py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 disabled:opacity-50"
                >
                  {joinRoomMutation.isPending ? 'Подключение...' : 'Присоединиться'}
                </button>
              </>
            ) : amIHost ? (
              <p className="text-gray-600">Ожидаем, пока собеседник присоединится...</p>
            ) : (
              <p className="text-gray-600">Комната создана, ожидание участников.</p>
            )}
          </div>
        )}

        {room.status === 'WAITING_CONSENT' && !dialogueStarted && (
          <div className="space-y-4">
            <p className="text-gray-700">Для начала диалога оба участника должны подтвердить готовность.</p>
            {wsStatus !== 'connected' && (
              <p className="text-sm text-amber-700">Подключение к серверу... Кнопка станет доступна после установки соединения.</p>
            )}
            {!haveIConsented ? (
              <button
                onClick={sendConsentStart}
                disabled={wsStatus !== 'connected'}
                className="px-6 py-2 bg-green-600 text-white rounded font-medium hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Я готов(а) начать
              </button>
            ) : (
              <button
                onClick={sendConsentRevoke}
                disabled={wsStatus !== 'connected'}
                className="px-6 py-2 bg-gray-200 text-gray-800 rounded font-medium hover:bg-gray-300 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Отменить готовность
              </button>
            )}
          </div>
        )}

        {(room.status === 'ACTIVE' || dialogueStarted) && (
          <div className="w-full h-full flex flex-col items-stretch text-left">
            <DialogueTranscript
              historyTurns={turnsPage?.items || []}
              liveTurns={liveTurns}
              otherDrafts={otherDrafts}
              aiThinking={aiThinking}
              myParticipantId={myParticipantId}
              participants={participants}
            />

            {!archived && !endProposerParticipantId && (
              <div className="flex justify-end pt-2 pb-4 shrink-0 px-4">
                <button
                  onClick={proposeEnd}
                  className="text-xs text-red-600 hover:text-red-800 underline"
                >
                  Завершить диалог
                </button>
              </div>
            )}

            {!archived && endProposerParticipantId && (
              <div className="bg-yellow-50 border border-yellow-200 p-4 shrink-0 rounded-lg mx-4 mb-4 flex items-center justify-between">
                {endProposerParticipantId !== myParticipantId ? (
                  <>
                    <span className="text-sm text-yellow-800 font-medium">Собеседник предлагает завершить диалог</span>
                    <div className="flex gap-2">
                      <button onClick={declineEnd} className="px-3 py-1.5 bg-white border border-gray-300 rounded text-sm hover:bg-gray-50">Продолжить</button>
                      <button onClick={agreeEnd} className="px-3 py-1.5 bg-red-600 text-white rounded text-sm hover:bg-red-700">Согласиться</button>
                    </div>
                  </>
                ) : (
                  <span className="text-sm text-yellow-800 font-medium w-full text-center">Ожидаем ответа собеседника на предложение завершить диалог...</span>
                )}
              </div>
            )}

            <div className="shrink-0 pt-2 border-t border-gray-200 px-4 pb-4 bg-white rounded-b-lg">
              <PairedComposer
                isActive={room.status === 'ACTIVE' || dialogueStarted}
                archived={archived}
                hasFloor={currentFloorParticipantId === myParticipantId}
                aiThinking={aiThinking}
                onDraftUpsert={upsertDraft}
                onSubmit={() => finishThought(maxSeq + 1)}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
