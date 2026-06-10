import type { TurnResponse } from '../types'
import { TurnBubble } from './TurnBubble'

interface TranscriptProps {
  turns: TurnResponse[]
}

export const Transcript = ({ turns }: TranscriptProps) => {
  if (turns.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500">
        Здесь пока нет сообщений. Напишите что-нибудь, чтобы начать диалог.
      </div>
    )
  }

  // Sort by sequence number just in case
  const sortedTurns = [...turns].sort((a, b) => a.seq - b.seq)

  return (
    <div className="flex flex-col w-full p-4 space-y-2">
      {sortedTurns.map((turn) => (
        <TurnBubble key={turn.id} turn={turn} />
      ))}
    </div>
  )
}
