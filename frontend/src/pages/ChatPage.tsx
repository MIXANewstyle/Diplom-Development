import { useRooms } from '../features/chat/hooks/useRooms'
import { useCreateSoloRoom } from '../features/chat/hooks/useCreateSoloRoom'
import { RoomList } from '../features/chat/components/RoomList'

export const ChatPage = () => {
  const { data: rooms, isLoading, isError } = useRooms()
  const createRoomMutation = useCreateSoloRoom()

  const handleCreateRoom = () => {
    createRoomMutation.mutate()
  }

  return (
    <div className="max-w-4xl mx-auto py-8 px-4">
      <div className="flex justify-between items-center mb-8">
        <h1 className="text-3xl font-bold text-gray-900">Чат</h1>
        <button
          onClick={handleCreateRoom}
          disabled={createRoomMutation.isPending}
          className="px-6 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors shadow"
        >
          {createRoomMutation.isPending ? 'Создание...' : 'Новая соло-сессия'}
        </button>
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
