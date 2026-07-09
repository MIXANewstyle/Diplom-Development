import { useEffect, useRef } from 'react'
import type { TurnResponse } from '../types'
import { TurnBubble } from './TurnBubble'

interface Props {
  historyTurns: TurnResponse[]
  liveTurns: TurnResponse[]
  otherDrafts: Record<string, string>
  aiThinking: boolean
  myParticipantId?: string
  participants: any[]
  pendingSentTurn?: TurnResponse | null
}

export const DialogueTranscript = ({
  historyTurns,
  liveTurns,
  otherDrafts,
  aiThinking,
  myParticipantId,
  participants,
  pendingSentTurn,
}: Props) => {
  const containerRef = useRef<HTMLDivElement>(null)

  // Merge history + live turns, deduplicated by id (live wins on conflict).
  const mergedMap = new Map<string, TurnResponse>()
  historyTurns.forEach((t) => mergedMap.set(t.id, t))
  liveTurns.forEach((t) => mergedMap.set(t.id, t))

  const serverTurns = Array.from(mergedMap.values()).sort((a, b) => a.seq - b.seq)

  // Splice in the optimistic bubble unless the server has already delivered the
  // real turn for the same seq (matched by seq, different id means it's the real one).
  const pendingIsSuperseded =
    !pendingSentTurn ||
    serverTurns.some((t) => t.seq === pendingSentTurn.seq && t.id !== pendingSentTurn.id)

  const sortedTurns = pendingSentTurn && !pendingIsSuperseded
    ? [...serverTurns, pendingSentTurn].sort((a, b) => a.seq - b.seq)
    : serverTurns

  // Auto-scroll to bottom
  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight
    }
  }, [sortedTurns, otherDrafts, aiThinking, pendingSentTurn])

  return (
    <div
      ref={containerRef}
      className="flex-1 overflow-y-auto bg-gray-50 p-4 rounded-t-lg rounded-b-none flex flex-col gap-4"
    >
      {sortedTurns.length === 0 && Object.keys(otherDrafts).length === 0 && !aiThinking && (
        <div className="text-center text-gray-500 py-8">
          Нет сообщений. Вы можете начать диалог.
        </div>
      )}

      {sortedTurns.map((turn) => {
        const isMine = turn.participantId === myParticipantId
        const participant = participants.find((p) => p.id === turn.participantId)
        const displayName = isMine ? 'Вы' : (participant?.displayName || 'Собеседник')

        return (
          <TurnBubble
            key={turn.id}
            turn={turn}
            isMine={isMine}
            displayName={displayName}
          />
        )
      })}

      {/* Other participant's drafts */}
      {Object.entries(otherDrafts).map(([bubbleId, text]) => {
        if (!text) return null
        return (
          <div key={bubbleId} className="flex justify-start w-full">
            <div className="max-w-[80%] bg-white border border-gray-200 text-gray-500 p-3 rounded-lg rounded-tl-none shadow-sm opacity-70">
              <div className="text-xs font-bold mb-1 text-gray-400">Собеседник (печатает...)</div>
              <div className="whitespace-pre-wrap break-words font-mono text-sm">{text}</div>
            </div>
          </div>
        )
      })}

      {/* AI Thinking Indicator */}
      {aiThinking && (
        <div className="flex flex-col w-full mb-4 items-start">
          <div className="max-w-[80%] rounded-2xl px-4 py-3 bg-gray-100 text-gray-800 rounded-tl-sm border border-gray-200">
            <div className="flex items-center gap-1.5 h-4">
              <div className="w-1.5 h-1.5 bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
              <div className="w-1.5 h-1.5 bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
              <div className="w-1.5 h-1.5 bg-gray-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
            </div>
          </div>
          <div className="text-xs text-gray-500 mt-1 px-1 text-left">
            ИИ печатает
          </div>
        </div>
      )}
    </div>
  )
}
