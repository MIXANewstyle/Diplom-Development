import { useAuthStore } from '../shared/stores/authStore'

export function ProfilePage() {
  const { user } = useAuthStore()

  if (!user) return null

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Мой профиль</h1>
      
      <div className="bg-white border border-gray-200 rounded-lg p-6 space-y-4">
        <div>
          <div className="text-sm text-gray-500">Email</div>
          <div>{user.email}</div>
        </div>
        <div>
          <div className="text-sm text-gray-500">Роль</div>
          <div>{user.role}</div>
        </div>
        <div>
          <div className="text-sm text-gray-500">ID</div>
          <div className="font-mono text-sm">{user.id}</div>
        </div>
      </div>

      <div className="bg-gray-100 text-gray-600 p-4 rounded-lg text-sm">
        Редактирование профиля — следующий этап разработки
      </div>
    </div>
  )
}
