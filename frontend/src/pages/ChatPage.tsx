import { useRooms } from '../features/chat/hooks/useRooms'
import { useCreateSoloRoom } from '../features/chat/hooks/useCreateSoloRoom'
import { useCreatePairedRoom } from '../features/chat/hooks/useCreatePairedRoom'
import { RoomList } from '../features/chat/components/RoomList'
import { useFriends } from '../features/social/hooks'
import { useState } from 'react'
import { Link } from 'react-router-dom'

export const ChatPage = () => {
  const { data: rooms, isLoading, isError } = useRooms()
  const createRoomMutation = useCreateSoloRoom()
  
  const { data: friendsData } = useFriends()
  const createPairedRoomMutation = useCreatePairedRoom()
  const [selectedFriendId, setSelectedFriendId] = useState('')

  const handleCreateRoom = () => {
    createRoomMutation.mutate()
  }

  const handleCreatePairedRoom = () => {
    if (selectedFriendId) {
      createPairedRoomMutation.mutate(selectedFriendId)
    }
  }

  const friends = friendsData?.friends || []

  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-8 gap-4">
        <h1 className="text-3xl font-bold text-gray-900">Чат</h1>
        <div className="flex flex-col md:flex-row gap-4 w-full md:w-auto">
          <button
            onClick={handleCreateRoom}
            disabled={createRoomMutation.isPending}
            className="px-6 py-2 bg-gray-100 text-gray-800 rounded-lg font-medium hover:bg-gray-200 disabled:opacity-50 transition-colors shadow-sm whitespace-nowrap"
          >
            {createRoomMutation.isPending ? 'Создание...' : 'Новая соло-сессия'}
          </button>

          <div className="flex flex-col sm:flex-row gap-2 p-3 bg-blue-50 border border-blue-100 rounded-lg">
            {friends.length > 0 ? (
              <>
                <select
                  value={selectedFriendId}
                  onChange={(e) => setSelectedFriendId(e.target.value)}
                  className="px-3 py-2 border border-blue-200 rounded text-sm bg-white focus:ring-2 focus:ring-blue-500 outline-none w-full sm:w-48"
                >
                  <option value="" disabled>Выберите друга</option>
                  {friends.map((f) => (
                    <option key={f.id} value={f.id}>{f.username}</option>
                  ))}
                </select>
                <button
                  onClick={handleCreatePairedRoom}
                  disabled={!selectedFriendId || createPairedRoomMutation.isPending}
                  className="w-full sm:w-auto px-4 py-2 bg-blue-600 text-white rounded font-medium text-sm hover:bg-blue-700 disabled:opacity-50 transition-colors shadow-sm"
                >
                  {createPairedRoomMutation.isPending ? 'Запуск...' : 'Новая парная комната'}
                </button>
              </>
            ) : (
              <div className="text-sm text-blue-800 flex items-center px-2">
                У вас пока нет друзей.{' '}
                <Link to="/friends" className="ml-1 underline font-medium hover:text-blue-900">
                  Добавить
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>

      {isLoading && (
        <div className="text-center py-8 text-gray-500">Загрузка сессий...</div>
      )}
      {isError && (
        <div className="text-center py-8 text-red-500">
          Ошибка при загрузке списка сессий.
        </div>
      )}
      {rooms && <RoomList rooms={rooms} />}
    </div>
  )
}
