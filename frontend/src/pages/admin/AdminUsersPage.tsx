import { useState } from 'react'
import { resolveMediaUrl } from '../../shared/lib/mediaUrl'
import { useUserSearch, useUpdateUserRole, useUpdateUserStatus } from '../../features/admin/hooks'
import { ROLE_OPTIONS, STATUS_OPTIONS } from '../../features/admin/types'

export function AdminUsersPage() {
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  
  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setDebouncedSearch(searchTerm)
  }

  const { data: users, isLoading, error } = useUserSearch(debouncedSearch)

  return (
    <div className="space-y-6">
      <div className="bg-blue-50 p-4 rounded text-sm text-blue-800">
        <p><strong>Примечание:</strong> Поиск пользователей не отображает их текущую роль или статус. При изменении роли пользователя, ему потребуется заново войти в систему, чтобы изменения вступили в силу.</p>
      </div>

      <form onSubmit={handleSearchSubmit} className="flex gap-2">
        <input
          type="text"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          placeholder="Поиск по имени пользователя..."
          className="flex-1 border border-gray-300 rounded px-3 py-2"
        />
        <button type="submit" className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          Найти
        </button>
      </form>

      {isLoading && <div>Загрузка...</div>}
      {error && <div className="text-red-600">Ошибка: {(error as any)?.response?.data?.message || error.message}</div>}

      {users && users.length === 0 && (
        <div className="text-gray-500">Пользователи не найдены.</div>
      )}

      {users && users.length > 0 && (
        <div className="space-y-4">
          {users.map((user) => (
            <UserRow key={user.id} user={user} />
          ))}
        </div>
      )}
    </div>
  )
}

function UserRow({ user }: { user: { id: string, username: string, avatarUrl?: string | null } }) {
  const [roleId, setRoleId] = useState<number>(ROLE_OPTIONS[0].id)
  const [statusId, setStatusId] = useState<number>(STATUS_OPTIONS[0].id)
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null)

  const updateRole = useUpdateUserRole()
  const updateStatus = useUpdateUserStatus()

  const handleUpdateRole = async () => {
    try {
      await updateRole.mutateAsync({ userId: user.id, roleId })
      setMessage({ type: 'success', text: 'Роль успешно обновлена. Пользователю нужно перелогиниться.' })
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка при обновлении роли' })
    }
  }

  const handleUpdateStatus = async () => {
    try {
      await updateStatus.mutateAsync({ userId: user.id, statusId })
      setMessage({ type: 'success', text: 'Статус успешно обновлен.' })
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка при обновлении статуса' })
    }
  }

  return (
    <div className="flex flex-col lg:flex-row gap-4 items-start lg:items-center p-4 border border-gray-200 rounded">
      <div className="flex items-center gap-3 flex-1 w-full">
        <img 
          src={resolveMediaUrl(user.avatarUrl) || `https://api.dicebear.com/7.x/identicon/svg?seed=${user.id}`} 
          alt={user.username} 
          className="w-10 h-10 rounded-full"
        />
        <div className="font-medium">{user.username}</div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-gray-500">Роль:</span>
        <select 
          value={roleId} 
          onChange={(e) => setRoleId(Number(e.target.value))}
          className="border border-gray-300 rounded px-2 py-1 text-sm"
        >
          {ROLE_OPTIONS.map((opt) => (
            <option key={opt.id} value={opt.id}>{opt.label}</option>
          ))}
        </select>
        <button 
          onClick={handleUpdateRole}
          disabled={updateRole.isPending}
          className="bg-gray-200 hover:bg-gray-300 px-3 py-1 rounded text-sm disabled:opacity-50 whitespace-nowrap"
        >
          Применить
        </button>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-gray-500">Статус:</span>
        <select 
          value={statusId} 
          onChange={(e) => setStatusId(Number(e.target.value))}
          className="border border-gray-300 rounded px-2 py-1 text-sm"
        >
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt.id} value={opt.id}>{opt.label}</option>
          ))}
        </select>
        <button 
          onClick={handleUpdateStatus}
          disabled={updateStatus.isPending}
          className="bg-gray-200 hover:bg-gray-300 px-3 py-1 rounded text-sm disabled:opacity-50 whitespace-nowrap"
        >
          Применить
        </button>
      </div>

      {message && (
        <div className={`text-sm w-full lg:w-auto ${message.type === 'error' ? 'text-red-600' : 'text-green-600'}`}>
          {message.text}
        </div>
      )}
    </div>
  )
}
