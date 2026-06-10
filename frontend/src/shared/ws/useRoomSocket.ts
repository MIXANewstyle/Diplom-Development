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
        stompClient.subscribe(`/topic/rooms/${roomId}`, (msg) => {
          setLastEvent(msg)
          // For now just logging it to prove reception
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
          setSnapshot(msg as RoomStateSnapshot)
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

  return { status, snapshot, lastEvent, error }
}
