import { useEffect, useRef, useState } from 'react'
import type { StompSubscription } from '@stomp/stompjs'
import { stompClient } from './stompClient'
import { wsAssistantTurnToResponse, wsUserTurnToResponse } from './normalizeTurn'
import type { RoomResponse, TurnResponse } from '../../features/chat/types'

export interface RoomStateSnapshot {
  roomId: string
  type: RoomResponse['type']
  status: RoomResponse['status']
  phase: string | null
  currentFloorParticipantId: string | null
  participants: any[]
  recentTurns: TurnResponse[]
  onlineParticipantIds?: string[]
}

type ConnectionStatus = 'connecting' | 'connected' | 'disconnected'

const OFFLINE_DEBOUNCE_MS = 2000

export const useRoomSocket = (
  roomId: string,
  myParticipantId?: string,
  authTokenOverride?: string | null
) => {
  const myParticipantIdRef = useRef<string | undefined>(myParticipantId)
  const ownDraftBubbleIdsRef = useRef(new Set<string>())
  const [status, setStatus] = useState<ConnectionStatus>('disconnected')
  const [snapshot, setSnapshot] = useState<RoomStateSnapshot | null>(null)
  const [lastEvent, setLastEvent] = useState<unknown>(null)
  const [error, setError] = useState<string | null>(null)

  const [consentByParticipant, setConsentByParticipant] = useState<Record<string, string | null>>({})
  const [onlineParticipants, setOnlineParticipants] = useState<Set<string>>(new Set())
  const [dialogueStarted, setDialogueStarted] = useState(false)
  const [currentFloorParticipantId, setCurrentFloorParticipantId] = useState<string | null>(null)

  const [maxSeq, setMaxSeq] = useState<number>(0)
  const [liveTurns, setLiveTurns] = useState<TurnResponse[]>([])
  const [aiThinking, setAiThinking] = useState(false)
  const [otherDrafts, setOtherDrafts] = useState<Record<string, string>>({})
  const [endProposerParticipantId, setEndProposerParticipantId] = useState<string | null>(null)
  const [archived, setArchived] = useState(false)
  const [endedAt, setEndedAt] = useState<string | null>(null)

  const sendConsentStart = () => stompClient.publish(`/app/rooms/${roomId}/consent/start`, {})
  const sendConsentRevoke = () => stompClient.publish(`/app/rooms/${roomId}/consent/revoke`, {})
  const sendPresence = (away: boolean) => {
    if (!stompClient.isConnected()) return
    stompClient.publish(`/app/rooms/${roomId}/presence`, { away })
  }
  const upsertDraft = (bubbleId: string, text: string) => {
    ownDraftBubbleIdsRef.current.add(bubbleId)
    stompClient.publish(`/app/rooms/${roomId}/draft/upsert`, { bubbleId, text })
  }
  const deleteDraft = (bubbleId: string) => {
    ownDraftBubbleIdsRef.current.delete(bubbleId)
    stompClient.publish(`/app/rooms/${roomId}/draft/delete`, { bubbleId })
  }
  const finishThought = (turnSeq: number, text: string) => stompClient.publish(`/app/rooms/${roomId}/finish`, { turnSeq, text })
  const proposeEnd = () => stompClient.publish(`/app/rooms/${roomId}/end/propose`, {})
  const agreeEnd = () => stompClient.publish(`/app/rooms/${roomId}/end/agree`, {})
  const declineEnd = () => stompClient.publish(`/app/rooms/${roomId}/end/decline`, {})

  useEffect(() => {
    myParticipantIdRef.current = myParticipantId
  }, [myParticipantId])

  useEffect(() => {
    if (!roomId) return

    let isMounted = true
    const subscriptions: StompSubscription[] = []
    const offlineTimers = new Map<string, ReturnType<typeof setTimeout>>()

    ownDraftBubbleIdsRef.current = new Set()

    setStatus('connecting')
    setError(null)
    setOnlineParticipants(new Set())
    setConsentByParticipant({})
    setDialogueStarted(false)
    setLiveTurns([])
    setOtherDrafts({})
    setAiThinking(false)

    const markOnline = (participantId: string) => {
      const timer = offlineTimers.get(participantId)
      if (timer) {
        clearTimeout(timer)
        offlineTimers.delete(participantId)
      }
      setOnlineParticipants((prev) => new Set([...prev, participantId]))
    }

    const markOffline = (participantId: string) => {
      if (offlineTimers.has(participantId)) return
      const timer = setTimeout(() => {
        offlineTimers.delete(participantId)
        setOnlineParticipants((prev) => {
          const next = new Set(prev)
          next.delete(participantId)
          return next
        })
      }, OFFLINE_DEBOUNCE_MS)
      offlineTimers.set(participantId, timer)
    }

    const clearSubscriptions = () => {
      subscriptions.forEach((sub) => sub.unsubscribe())
      subscriptions.length = 0
    }

    const applyPresenceSnapshot = (ids: string[]) => {
      offlineTimers.forEach((timer) => clearTimeout(timer))
      offlineTimers.clear()
      setOnlineParticipants(new Set(ids.map(String)))
    }

    const setupSubscriptions = () => {
      clearSubscriptions()

      // Presence queue MUST be subscribed before the room topic.
      // Subscribing to the topic triggers a PRESENCE_SNAPSHOT to /user/queue/presence.
      const presenceSub = stompClient.subscribe('/user/queue/presence', (msg) => {
        if (msg?.type === 'PRESENCE_SNAPSHOT' && String(msg.roomId) === roomId && Array.isArray(msg.onlineParticipantIds)) {
          applyPresenceSnapshot(msg.onlineParticipantIds.map(String))
        }
      })
      if (presenceSub) subscriptions.push(presenceSub)

      const errorsSub = stompClient.subscribe('/user/queue/errors', (msg) => {
        console.error('[WS Error]', msg)
        setError(msg?.message || 'Unknown WebSocket error')
        if (msg?.errorType === 'SEQ_MISMATCH' && typeof msg?.expectedTurnSeq === 'number') {
          setMaxSeq(msg.expectedTurnSeq - 1)
          setAiThinking(false)
        }
      })
      if (errorsSub) subscriptions.push(errorsSub)

      const topicSub = stompClient.subscribe(`/topic/rooms/${roomId}`, (msg: any) => {
        setLastEvent(msg)
        if (msg?.type === 'CONSENT_UPDATED') {
          setConsentByParticipant((prev) => ({
            ...prev,
            [String(msg.participantId)]: msg.consentStartAt,
          }))
        } else if (msg?.type === 'PRESENCE_UPDATED') {
          const participantId = String(msg.participantId)
          if (msg.status === 'ONLINE') {
            markOnline(participantId)
          } else {
            markOffline(participantId)
          }
        } else if (msg?.type === 'DIALOGUE_STARTED') {
          setDialogueStarted(true)
          setCurrentFloorParticipantId(String(msg.currentFloorParticipantId))
        } else if (msg?.type === 'DRAFT_BROADCAST') {
          const bubbleId = String(msg.bubbleId)
          const senderId = String(msg.participantId)
          const isOwnDraft =
            ownDraftBubbleIdsRef.current.has(bubbleId) ||
            (!!myParticipantIdRef.current && senderId === myParticipantIdRef.current)

          if (isOwnDraft) {
            return
          }

          setOtherDrafts((prev) => {
            const next = { ...prev }
            if (msg.op === 'UPSERT') {
              next[bubbleId] = msg.text
            } else if (msg.op === 'DELETE') {
              delete next[bubbleId]
            }
            return next
          })
        } else if (msg?.type === 'AI_THINKING') {
          setAiThinking(true)
          ownDraftBubbleIdsRef.current = new Set()
          if (msg.userTurn) {
            const turn = wsUserTurnToResponse(msg.userTurn, roomId)
            setMaxSeq(turn.seq)
            setLiveTurns((prev) => [...prev, turn])
            setOtherDrafts({})
          }
        } else if (msg?.type === 'AI_RESPONSE') {
          setAiThinking(false)
          if (msg.assistantTurn) {
            const turn = wsAssistantTurnToResponse(msg.assistantTurn, roomId)
            setMaxSeq(turn.seq)
            setLiveTurns((prev) => [...prev, turn])
          }
        } else if (msg?.type === 'TURN_CHANGED') {
          setCurrentFloorParticipantId(String(msg.currentFloorParticipantId))
        } else if (msg?.type === 'END_PROPOSED') {
          setEndProposerParticipantId(String(msg.proposerParticipantId))
        } else if (msg?.type === 'END_DECLINED') {
          setEndProposerParticipantId(null)
          if (msg.currentFloorParticipantId) {
            setCurrentFloorParticipantId(String(msg.currentFloorParticipantId))
          }
        } else if (msg?.type === 'DIALOGUE_ARCHIVED') {
          setArchived(true)
          setEndedAt(msg.endedAt)
          setEndProposerParticipantId(null)
        } else if (msg?.type === 'AI_ERROR' || msg?.type === 'LIMIT') {
          setAiThinking(false)
          setError(msg.message)
        }
      })
      if (topicSub) subscriptions.push(topicSub)

      const stateSub = stompClient.subscribe(`/app/rooms/${roomId}/state`, (msg) => {
        const snap = msg as RoomStateSnapshot
        setSnapshot(snap)

        if (snap.recentTurns?.length) {
          const highest = snap.recentTurns.reduce((max, t) => Math.max(max, t.seq), 0)
          setMaxSeq((prev) => Math.max(prev, highest))
        }

        if (snap.onlineParticipantIds?.length) {
          applyPresenceSnapshot(snap.onlineParticipantIds.map(String))
        }

        if (snap.participants) {
          const consentMap: Record<string, string | null> = {}
          snap.participants.forEach((p: any) => {
            consentMap[p.id] = p.consentStartAt
          })
          setConsentByParticipant(consentMap)
        }
        if (snap.status === 'ACTIVE' || snap.phase === 'DIALOGUE') {
          setDialogueStarted(true)
        }
        if (snap.status === 'ARCHIVED') {
          setArchived(true)
        }
        setCurrentFloorParticipantId(snap.currentFloorParticipantId)
      })
      if (stateSub) subscriptions.push(stateSub)
    }

    const unsubscribeReconnect = stompClient.onReconnect(() => {
      if (isMounted) {
        setupSubscriptions()
        // handleSubscribe always broadcasts ONLINE; correct if this tab is hidden
        if (document.hidden) {
          sendPresence(true)
        }
      }
    })

    const onVisibilityChange = () => {
      if (document.hidden) {
        sendPresence(true)
      } else {
        sendPresence(false)
      }
    }
    document.addEventListener('visibilitychange', onVisibilityChange)

    const connect = async () => {
      try {
        await stompClient.connect(authTokenOverride ?? undefined)
        if (!isMounted) return
        setStatus('connected')
      } catch (err) {
        if (isMounted) {
          setStatus('disconnected')
          setError(err instanceof Error ? err.message : 'Failed to connect')
        }
      }
    }

    connect()

    return () => {
      isMounted = false
      document.removeEventListener('visibilitychange', onVisibilityChange)
      unsubscribeReconnect()
      clearSubscriptions()
      offlineTimers.forEach((timer) => clearTimeout(timer))
      offlineTimers.clear()
      stompClient.disconnect()
      setStatus('disconnected')
    }
  }, [roomId, authTokenOverride])

  return {
    status,
    snapshot,
    lastEvent,
    error,
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
    endedAt,
    sendConsentStart,
    sendConsentRevoke,
    upsertDraft,
    deleteDraft,
    finishThought,
    proposeEnd,
    agreeEnd,
    declineEnd,
  }
}
