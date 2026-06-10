import { Link } from 'react-router-dom'
import type { RoomSummaryResponse } from '../types'
import { formatDate } from '../../../shared/lib/format'

interface RoomListProps {
  rooms: RoomSummaryResponse[]
}

export const RoomList = ({ rooms }: RoomListProps) => {
  if (rooms.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500 bg-white rounded shadow">
        Нет активных сессий. Начните новую!
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {rooms.map((room) => (
        <Link
          key={room.id}
          to={`/chat/${room.id}`}
          className="block p-4 bg-white rounded shadow hover:shadow-md transition-shadow"
        >
          <div className="flex justify-between items-center mb-2">
            <span className="font-semibold text-gray-800">
              {room.type === 'SOLO' ? 'Соло сессия' : 'Парная сессия'}
            </span>
            <span
              className={`px-2 py-1 text-xs rounded-full ${
                room.status === 'ACTIVE'
                  ? 'bg-green-100 text-green-800'
                  : 'bg-gray-100 text-gray-800'
              }`}
            >
              {room.status}
            </span>
          </div>
          <div className="text-sm text-gray-500">
            Создана: {formatDate(room.createdAt)}
          </div>
        </Link>
      ))}
    </div>
  )
}
