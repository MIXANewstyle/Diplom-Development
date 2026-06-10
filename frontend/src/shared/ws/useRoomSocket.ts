import { useEffect, useState } from 'react'
import { stompClient } from './stompClient'
import type { RoomResponse, TurnResponse } from '../../features/chat/types'

export interface RoomStateSnapshot {
  roomId: string
  type: RoomResponse['type']
  status: RoomResponse['status']
  phase: string | null
  currentFloorParticipantId: string | null
  participants: any[] // Simplified for now
  recentTurns: TurnResponse[]
}

type ConnectionStatus = 'connecting' | 'connected' | 'disconnected'

export const useRoomSocket = (roomId: string) => {
  const [status, setStatus] = useState<ConnectionStatus>('disconnected')
  const [snapshot, setSnapshot] = useState<RoomStateSnapshot | null>(null)
  const [lastEvent, setLastEvent] = useState<unknown>(null)
  const [error, setError] = useState<string | null>(null)

  const [consentByParticipant, setConsentByParticipant] = useState<Record<string, string | null>>({})
  const [dialogueStarted, setDialogueStarted] = useState(false)
  const [currentFloorParticipantId, setCurrentFloorParticipantId] = useState<string | null>(null)

  // Sub-step 08c specific states
  const [maxSeq, setMaxSeq] = useState<number>(0)
  const [liveTurns, setLiveTurns] = useState<TurnResponse[]>([])
  const [aiThinking, setAiThinking] = useState(false)
  const [otherDrafts, setOtherDrafts] = useState<Record<string, string>>({})
  const [endProposerParticipantId, setEndProposerParticipantId] = useState<string | null>(null)
  const [archived, setArchived] = useState(false)
  const [endedAt, setEndedAt] = useState<string | null>(null)

  const sendConsentStart = () => stompClient.publish(`/app/rooms/${roomId}/consent/start`, {})
  const sendConsentRevoke = () => stompClient.publish(`/app/rooms/${roomId}/consent/revoke`, {})

  // Dialogue publishers
  const upsertDraft = (bubbleId: string, text: string) => stompClient.publish(`/app/rooms/${roomId}/draft/upsert`, { bubbleId, text })
  const deleteDraft = (bubbleId: string) => stompClient.publish(`/app/rooms/${roomId}/draft/delete`, { bubbleId })
  const finishThought = (turnSeq: number) => stompClient.publish(`/app/rooms/${roomId}/finish`, { turnSeq })
  const proposeEnd = () => stompClient.publish(`/app/rooms/${roomId}/end/propose`, {})
  const agreeEnd = () => stompClient.publish(`/app/rooms/${roomId}/end/agree`, {})
  const declineEnd = () => stompClient.publish(`/app/rooms/${roomId}/end/decline`, {})

  useEffect(() => {
    if (!roomId) return

    let isMounted = true
    setStatus('connecting')
    setError(null)

    const connectAndSubscribe = async () => {
      try {
        await stompClient.connect()
        if (!isMounted) return

        setStatus('connected')

        // 1. Subscribe to room broadcasts
        stompClient.subscribe(`/topic/rooms/${roomId}`, (msg: any) => {
          setLastEvent(msg)
          if (msg?.type === 'CONSENT_UPDATED') {
            setConsentByParticipant((prev) => ({
              ...prev,
              [msg.participantId]: msg.consentStartAt,
            }))
          } else if (msg?.type === 'DIALOGUE_STARTED') {
            setDialogueStarted(true)
            setCurrentFloorParticipantId(msg.currentFloorParticipantId)
          } else if (msg?.type === 'DRAFT_BROADCAST') {
            setOtherDrafts((prev) => {
              const next = { ...prev }
              if (msg.op === 'UPSERT') {
                next[msg.bubbleId] = msg.text
              } else if (msg.op === 'DELETE') {
                delete next[msg.bubbleId]
              }
              return next
            })
          } else if (msg?.type === 'AI_THINKING') {
            setAiThinking(true)
            if (msg.userTurn) {
              setMaxSeq(msg.userTurn.seq)
              setLiveTurns((prev) => [...prev, msg.userTurn])
              // Clear drafts for this participant
              // Since we don't know whose drafts they are easily here, 
              // we just clear all drafts on submit.
              setOtherDrafts({})
            }
          } else if (msg?.type === 'AI_RESPONSE') {
            setAiThinking(false)
            if (msg.assistantTurn) {
              setMaxSeq(msg.assistantTurn.seq)
              setLiveTurns((prev) => [...prev, msg.assistantTurn])
            }
          } else if (msg?.type === 'TURN_CHANGED') {
            setCurrentFloorParticipantId(msg.currentFloorParticipantId)
          } else if (msg?.type === 'END_PROPOSED') {
            setEndProposerParticipantId(msg.proposerParticipantId)
          } else if (msg?.type === 'END_DECLINED') {
            setEndProposerParticipantId(null)
            if (msg.currentFloorParticipantId) {
              setCurrentFloorParticipantId(msg.currentFloorParticipantId)
            }
          } else if (msg?.type === 'DIALOGUE_ARCHIVED') {
            setArchived(true)
            setEndedAt(msg.endedAt)
            setEndProposerParticipantId(null)
          } else if (msg?.type === 'AI_ERROR' || msg?.type === 'LIMIT') {
            setAiThinking(false)
            setError(msg.message)
          }

          console.log('[Room Event]', msg)
        })

        // 2. Subscribe to user errors
        stompClient.subscribe('/user/queue/errors', (msg) => {
          console.error('[WS Error]', msg)
          setError(msg?.message || 'Unknown WebSocket error')
        })

        // 3. Subscribe to state snapshot
        stompClient.subscribe(`/app/rooms/${roomId}/state`, (msg) => {
          console.log('[Room Snapshot]', msg)
          const snap = msg as RoomStateSnapshot
          setSnapshot(snap)

          // Seed local state from snapshot
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

      } catch (err) {
        if (isMounted) {
          setStatus('disconnected')
          setError(err instanceof Error ? err.message : 'Failed to connect')
        }
      }
    }

    connectAndSubscribe()

    return () => {
      isMounted = false
      stompClient.disconnect()
      setStatus('disconnected')
    }
  }, [roomId])

  return { 
    status, 
    snapshot, 
    lastEvent, 
    error,
    consentByParticipant,
    dialogueStarted,
    currentFloorParticipantId,
    maxSeq,
    setMaxSeq, // Let the view seed it from history
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
    declineEnd
  }
}
