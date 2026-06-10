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

  const sendConsentStart = () => stompClient.publish(`/app/rooms/${roomId}/consent/start`, {})
  const sendConsentRevoke = () => stompClient.publish(`/app/rooms/${roomId}/consent/revoke`, {})

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
    sendConsentStart,
    sendConsentRevoke
  }
}
