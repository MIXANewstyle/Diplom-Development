import type { TurnResponse } from '../types'
import { formatDate } from '../../../shared/lib/format'

interface TurnBubbleProps {
  turn: TurnResponse
  isMine?: boolean
  displayName?: string
}

export const TurnBubble = ({ turn, isMine, displayName }: TurnBubbleProps) => {
  const isUser = turn.role === 'USER'
  const isMineEffective = isMine ?? isUser
  const label = displayName || (isUser ? 'Вы' : 'Ассистент')

  return (
    <div className={`flex flex-col w-full mb-4 ${isMineEffective ? 'items-end' : 'items-start'}`}>
      <div
        className={`max-w-[80%] rounded-2xl px-4 py-2 ${
          isMineEffective
            ? 'bg-blue-600 text-white rounded-tr-sm'
            : 'bg-gray-100 text-gray-800 rounded-tl-sm border border-gray-200'
        }`}
      >
        <div className="whitespace-pre-wrap break-words">{turn.content}</div>
        <div
          className={`text-[10px] mt-1 ${
            isMineEffective ? 'text-blue-200 text-right' : 'text-gray-400 text-left'
          }`}
        >
          {formatDate(turn.createdAt)}
        </div>
      </div>
      <div className={`text-xs text-gray-500 mt-1 px-1 ${isMineEffective ? 'text-right' : 'text-left'}`}>
        {label}
      </div>
    </div>
  )
}
