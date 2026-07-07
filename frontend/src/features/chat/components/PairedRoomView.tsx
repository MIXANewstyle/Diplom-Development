import { useEffect, useState } from 'react'
import { useRoom } from '../hooks/useRoom'
import { useJoinRoom } from '../hooks/useJoinRoom'
import { useRoomSocket } from '../../../shared/ws/useRoomSocket'
import { useAuthStore } from '../../../shared/stores/authStore'
import { useGuestSessionStore } from '../../../shared/stores/guestSessionStore'
import { useQueryClient, useMutation } from '@tanstack/react-query'
import { useTurns } from '../hooks/useTurns'
import { DialogueTranscript } from './DialogueTranscript'
import { PairedComposer } from './PairedComposer'
import { InvitePanel } from './InvitePanel'
import { Pencil, Check, X } from 'lucide-react'
import { renameRoom } from '../api'
import { getErrorMessage } from '../../../shared/lib/errors'
import type { TurnResponse } from '../types'
import { resolveMediaUrl } from '../../../shared/lib/mediaUrl'

interface Props {
  roomId: string
}

export const PairedRoomView = ({ roomId }: Props) => {
  const me = useAuthStore((s) => s.user)
  const authToken = useAuthStore((s) => s.token)
  const myId = me?.id
  const guestSession = useGuestSessionStore((s) => s.getSession(roomId))
  const isGuest = !authToken && !!guestSession
  const guestAuthToken = isGuest ? guestSession?.token : undefined

  const { data: room, isLoading: isRoomLoading, isError: isRoomError } = useRoom(
    roomId,
    guestAuthToken
  )
  const joinRoomMutation = useJoinRoom(roomId)

  const myParticipantId = isGuest
    ? guestSession?.participantId
    : room?.participants?.find((p) => p.userId === myId)?.id

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
  } = useRoomSocket(roomId, myParticipantId, guestAuthToken)

  const { data: turnsPage } = useTurns(roomId, 0, 50, guestAuthToken)

  // Optimistic "sent" bubble: created immediately on submit, replaced once the
  // server turn arrives (matched by seq) to avoid a blank gap in the transcript.
  const [pendingSentTurn, setPendingSentTurn] = useState<TurnResponse | null>(null)

  // Seed maxSeq from history
  useEffect(() => {
    if (turnsPage?.items) {
      const highest = turnsPage.items.reduce((max, t) => Math.max(max, t.seq), 0)
      setMaxSeq((prev) => Math.max(prev, highest))
    }
  }, [turnsPage?.items, setMaxSeq])

  // Drop the optimistic bubble once the authoritative server turn (same seq,
  // different id) appears in either live or history turns.
  useEffect(() => {
    if (!pendingSentTurn) return
    const allServerTurns = [...(turnsPage?.items ?? []), ...liveTurns]
    const confirmed = allServerTurns.some(
      (t) => t.seq === pendingSentTurn.seq && t.id !== pendingSentTurn.id
    )
    if (confirmed) setPendingSentTurn(null)
  }, [liveTurns, turnsPage?.items, pendingSentTurn])

  // Called by PairedComposer when the user submits a message.
  const handleComposerSubmit = (text: string, bubbleId: string) => {
    if (!myParticipantId) return
    // Show the message immediately as a sent bubble in both transcripts.
    setPendingSentTurn({
      id: bubbleId,
      roomId,
      seq: maxSeq + 1,
      role: 'USER',
      participantId: myParticipantId,
      content: text,
      promptTokens: null,
      completionTokens: null,
      createdAt: new Date().toISOString(),
    })
    // Trigger AI processing. The text is sent as a fallback in case the
    // draft buffer was not yet populated when the server processes finish.
    // The server clears the draft buffer itself after packaging, so we
    // do NOT call deleteDraft here (that was the original race bug).
    finishThought(maxSeq + 1, text)
  }

  const queryClient = useQueryClient()

  // Rename inline editing
  const [isEditingTitle, setIsEditingTitle] = useState(false)
  const [editTitle, setEditTitle] = useState('')
  const renameMutation = useMutation({
    mutationFn: (newTitle: string | null) => renameRoom(roomId, newTitle),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'room', roomId] })
      queryClient.invalidateQueries({ queryKey: ['chat', 'rooms'] })
      setIsEditingTitle(false)
    },
    onError: (err) => alert('Ошибка: ' + getErrorMessage(err)),
  })

  const startRename = () => {
    setEditTitle(room?.title || '')
    setIsEditingTitle(true)
  }
  const submitRename = () => {
    const trimmed = editTitle.trim()
    renameMutation.mutate(trimmed || null)
  }
  const cancelRename = () => setIsEditingTitle(false)

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
  const myParticipant = isGuest
    ? participants.find((p) => p.id === guestSession?.participantId)
    : participants.find((p) => p.userId === myId)

  // Am I the invitee who hasn't joined yet?
  const amIInvitee = !isGuest && myParticipant && myParticipant.joinedAt === null
  const amIHost = !isGuest && room.ownerUserId === myId
  
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
    : room.status === 'ARCHIVED' || archived
    ? 'Диалог завершён'
    : room.status

  return (
    <div className="max-w-4xl mx-auto h-[calc(100vh-80px)] flex flex-col py-4 px-2 md:px-4 gap-4 md:gap-6">
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
              <h1 className="text-xl font-bold text-gray-900">{room?.title || 'Парная сессия'}</h1>
              <button onClick={startRename} className="p-1 text-gray-400 hover:text-blue-600 opacity-0 group-hover/title:opacity-100 focus:opacity-100 transition-opacity shrink-0" title="Переименовать"><Pencil size={14} /></button>
            </div>
          )}
          <div className="text-sm text-gray-500">Статус: {statusDisplay}</div>
        </div>
        <div className="flex flex-col items-end text-xs text-gray-500 shrink-0">
          <div>WS: {wsStatus}</div>
          {wsError && <div className="text-red-500 break-all">WS Error: {wsError}</div>}
        </div>
      </div>

      <div className="flex flex-col md:flex-row gap-6">
        {participants.map((p) => {
          const isMe = isGuest
            ? p.id === guestSession?.participantId
            : p.userId === myId
          const participantName = p.displayName || p.guestDisplayName || '?'
          const hasConsented = !!consentByParticipant[p.id] || !!p.consentStartAt
          const isFloorHolder = currentFloorParticipantId === p.id
          const hasJoined = p.joinedAt !== null || (p.userId == null && !!p.guestDisplayName)
          const isOnline = hasJoined && (
            (isMe && wsStatus === 'connected') || onlineParticipants.has(p.id)
          )

          return (
            <div key={p.id} className="flex-1 bg-white border border-gray-200 rounded-lg p-4 shadow-sm flex items-center gap-4 relative">
              {p.avatarUrl ? (
                <img src={resolveMediaUrl(p.avatarUrl) || ''} alt={participantName} className="w-12 h-12 rounded-full object-cover" />
              ) : (
                <div className="w-12 h-12 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-bold text-lg">
                  {participantName.charAt(0).toUpperCase()}
                </div>
              )}
              <div className="flex-1">
                <div className="font-bold text-gray-900">
                  {participantName} {isMe && <span className="text-gray-400 font-normal text-sm">(Вы)</span>}
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
            ) : amIHost && participants.length < 2 ? (
              <InvitePanel roomId={roomId} />
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

        {(room.status === 'ACTIVE' || dialogueStarted || room.status === 'ARCHIVED' || archived || (turnsPage?.items?.length ?? 0) > 0) && (
          <div className="w-full h-full flex flex-col items-stretch text-left">
            {(room.status === 'ARCHIVED' || archived) && (
              <div className="text-center text-sm text-gray-500 pb-4 shrink-0 italic">
                Этот диалог завершён
              </div>
            )}
            <DialogueTranscript
              historyTurns={turnsPage?.items || []}
              liveTurns={liveTurns}
              otherDrafts={otherDrafts}
              aiThinking={aiThinking}
              myParticipantId={myParticipantId}
              participants={participants}
              pendingSentTurn={pendingSentTurn}
            />

            {!(room.status === 'ARCHIVED' || archived) && !endProposerParticipantId && (
              <div className="flex justify-end pt-2 pb-4 shrink-0 px-4">
                <button
                  onClick={proposeEnd}
                  className="text-xs text-red-600 hover:text-red-800 underline"
                >
                  Завершить диалог
                </button>
              </div>
            )}

            {!(room.status === 'ARCHIVED' || archived) && endProposerParticipantId && (
              <div className="bg-yellow-50 border border-yellow-200 p-4 shrink-0 rounded-lg mx-2 md:mx-4 mb-4 flex flex-wrap items-center justify-between gap-2">
                {endProposerParticipantId !== myParticipantId ? (
                  <>
                    <span className="text-sm text-yellow-800 font-medium">Собеседник предлагает завершить диалог</span>
                    <div className="flex gap-2 shrink-0">
                      <button onClick={declineEnd} className="px-3 py-1.5 bg-white border border-gray-300 rounded text-sm hover:bg-gray-50">Продолжить</button>
                      <button onClick={agreeEnd} className="px-3 py-1.5 bg-red-600 text-white rounded text-sm hover:bg-red-700">Согласиться</button>
                    </div>
                  </>
                ) : (
                  <span className="text-sm text-yellow-800 font-medium w-full text-center">Ожидаем ответа собеседника на предложение завершить диалог...</span>
                )}
              </div>
            )}

            {!(room.status === 'ARCHIVED' || archived) && (
              <div className="shrink-0 pt-2 border-t border-gray-200 px-2 md:px-4 pb-4 bg-white rounded-b-lg">
                <PairedComposer
                  isActive={room.status === 'ACTIVE' || dialogueStarted}
                  archived={archived}
                  hasFloor={currentFloorParticipantId === myParticipantId}
                  aiThinking={aiThinking}
                  endPending={!!endProposerParticipantId}
                  wsError={wsError}
                  onDraftUpsert={upsertDraft}
                  onSubmit={handleComposerSubmit}
                />
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
