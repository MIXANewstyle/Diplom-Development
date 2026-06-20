import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import type { RoomSummaryResponse } from '../types'
import { formatDate } from '../../../shared/lib/format'
import { deleteRoom } from '../api'
import { getErrorMessage } from '../../../shared/lib/errors'

interface RoomListProps {
  rooms: RoomSummaryResponse[]
}

export const RoomList = ({ rooms }: RoomListProps) => {
  const [deleteTarget, setDeleteTarget] = useState<RoomSummaryResponse | null>(null)
  const queryClient = useQueryClient()

  const deleteMutation = useMutation({
    mutationFn: (roomId: string) => deleteRoom(roomId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'rooms'] })
      setDeleteTarget(null)
    },
    onError: (error) => {
      alert('Ошибка при удалении: ' + getErrorMessage(error))
    },
  })

  if (rooms.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500 bg-white rounded shadow">
        Нет активных сессий. Начните новую!
      </div>
    )
  }

  const roomLabel = (room: RoomSummaryResponse) =>
    room.title || (room.type === 'SOLO' ? 'Соло сессия' : 'Парная сессия')

  return (
    <>
      <div className="space-y-4">
        {rooms.map((room) => (
          <div key={room.id} className="relative group">
            <Link
              to={`/chat/${room.id}`}
              className="block p-4 bg-white rounded shadow hover:shadow-md transition-shadow"
            >
              <div className="flex justify-between items-center mb-2">
                <div className="min-w-0 flex-1 mr-8">
                  <span className="font-semibold text-gray-800 block truncate">
                    {roomLabel(room)}
                  </span>
                  {room.title && (
                    <span className="text-xs text-gray-400">
                      {room.type === 'SOLO' ? 'Соло' : 'Парная'}
                    </span>
                  )}
                </div>
                <span
                  className={`px-2 py-1 text-xs rounded-full shrink-0 ${
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
            <button
              onClick={(e) => {
                e.preventDefault()
                e.stopPropagation()
                setDeleteTarget(room)
              }}
              className="absolute top-3 right-3 p-1.5 rounded-md text-gray-400 hover:text-red-600 hover:bg-red-50 opacity-0 group-hover:opacity-100 focus:opacity-100 transition-all"
              title="Удалить сессию"
            >
              <Trash2 size={16} />
            </button>
          </div>
        ))}
      </div>

      {/* Delete confirmation modal */}
      {deleteTarget && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          onClick={() => !deleteMutation.isPending && setDeleteTarget(null)}
        >
          <div
            className="bg-white rounded-xl shadow-xl max-w-md w-full p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 className="text-lg font-bold text-gray-900 mb-3">Удалить сессию?</h2>
            <p className="text-sm text-gray-700 mb-1">
              {deleteTarget.type === 'PAIRED' ? (
                <>
                  Переписка будет{' '}
                  <strong className="text-red-700">
                    безвозвратно удалена для обоих участников
                  </strong>
                  .
                </>
              ) : (
                <>
                  Переписка будет <strong className="text-red-700">безвозвратно удалена</strong>.
                </>
              )}
            </p>
            <p className="text-sm text-red-600 font-medium mb-6">
              Это действие необратимо.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeleteTarget(null)}
                disabled={deleteMutation.isPending}
                className="px-4 py-2 text-sm text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors disabled:opacity-50"
              >
                Отмена
              </button>
              <button
                onClick={() => deleteMutation.mutate(deleteTarget.id)}
                disabled={deleteMutation.isPending}
                className="px-4 py-2 text-sm text-white bg-red-600 rounded-lg hover:bg-red-700 transition-colors disabled:opacity-50"
              >
                {deleteMutation.isPending ? 'Удаление...' : 'Удалить'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
