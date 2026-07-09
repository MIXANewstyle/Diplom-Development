import { useState } from 'react'
import { resolveMediaUrl } from '../../shared/lib/mediaUrl'
import { PasswordInput } from '../../shared/components/PasswordInput'
import {
  useAdminUserSearch,
  useAdminUserDetails,
  useUpdateUserRole,
  useUpdateUserStatus,
  useResetUserPassword,
} from '../../features/admin/hooks'
import { ROLE_OPTIONS, STATUS_OPTIONS, type AdminUserSummary } from '../../features/admin/types'

export function AdminUsersPage() {
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [page, setPage] = useState(0)
  
  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setDebouncedSearch(searchTerm.trim())
    setPage(0)
  }

  const { data: pageData, isLoading, error } = useAdminUserSearch(debouncedSearch, page, 20)
  const users = pageData?.content || []

  return (
    <div className="space-y-6">
      <form onSubmit={handleSearchSubmit} className="flex gap-2">
        <input
          type="text"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          placeholder="Поиск по email, имени или UUID..."
          className="flex-1 border border-gray-300 rounded px-3 py-2"
        />
        <button type="submit" className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          Найти
        </button>
      </form>

      {isLoading && <div>Загрузка...</div>}
      {error && <div className="text-red-600">Ошибка: {(error as any)?.response?.data?.message || error.message}</div>}

      {users.length === 0 && !isLoading && (
        <div className="text-gray-500">Пользователи не найдены.</div>
      )}

      {users.length > 0 && (
        <div className="space-y-4">
          <div className="bg-white border rounded shadow-sm overflow-hidden overflow-x-auto">
            <table className="w-full text-left text-sm min-w-[600px]">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="p-3 font-medium text-gray-600">Пользователь</th>
                  <th className="p-3 font-medium text-gray-600">Email</th>
                  <th className="p-3 font-medium text-gray-600">Роль / Статус</th>
                  <th className="p-3 font-medium text-gray-600">Дата рег.</th>
                  <th className="p-3 font-medium text-gray-600 w-10"></th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {users.map((user) => (
                  <UserRow key={user.id} user={user} />
                ))}
              </tbody>
            </table>
          </div>

          {pageData && pageData.totalPages > 1 && (
            <div className="flex gap-4 items-center justify-center pt-4">
              <button
                disabled={pageData.first}
                onClick={() => setPage(p => p - 1)}
                className="px-3 py-1 border rounded disabled:opacity-50 hover:bg-gray-50"
              >
                Назад
              </button>
              <span className="text-sm">Страница {pageData.number + 1} из {pageData.totalPages}</span>
              <button
                disabled={pageData.last}
                onClick={() => setPage(p => p + 1)}
                className="px-3 py-1 border rounded disabled:opacity-50 hover:bg-gray-50"
              >
                Вперёд
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function UserRow({ user }: { user: AdminUserSummary }) {
  const [expanded, setExpanded] = useState(false)
  
  const roleLabel = ROLE_OPTIONS.find(r => r.id === user.roleId)?.label || 'Неизвестно'
  const statusLabel = STATUS_OPTIONS.find(s => s.id === user.statusId)?.label || 'Неизвестно'
  const isBlocked = user.statusId === 2
  const isActive = user.statusId === 1

  return (
    <>
      <tr className="hover:bg-gray-50 transition-colors">
        <td className="p-3">
          <div className="flex items-center gap-3">
            <img 
              src={resolveMediaUrl(user.avatarUrl) || `https://api.dicebear.com/7.x/identicon/svg?seed=${user.id}`} 
              alt={user.username || 'user'} 
              className="w-10 h-10 rounded-full bg-gray-200 flex-shrink-0"
            />
            <div className="min-w-0">
              <div className="font-medium text-gray-900 truncate">{user.fullName || '—'}</div>
              <div className="text-gray-500 text-xs truncate">@{user.username || '—'}</div>
            </div>
          </div>
        </td>
        <td className="p-3 text-gray-600 truncate max-w-[150px]" title={user.email}>{user.email}</td>
        <td className="p-3">
          <div className="flex flex-col items-start gap-1">
            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800">
              {roleLabel}
            </span>
            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${isBlocked ? 'bg-red-100 text-red-800' : isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
              {statusLabel}
            </span>
          </div>
        </td>
        <td className="p-3 text-gray-500 whitespace-nowrap">
          {new Date(user.createdAt).toLocaleDateString()}
        </td>
        <td className="p-3 text-right">
          <button 
            onClick={() => setExpanded(!expanded)}
            className="text-blue-600 hover:text-blue-800 text-sm whitespace-nowrap"
          >
            {expanded ? 'Скрыть' : 'Подробнее'}
          </button>
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={5} className="p-0 border-b-2 border-blue-200">
            <div className="bg-blue-50/50 p-4 shadow-inner">
              <UserDetailsPanel userId={user.id} />
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

function UserDetailsPanel({ userId }: { userId: string }) {
  const { data: details, isLoading, error } = useAdminUserDetails(userId)
  const updateRole = useUpdateUserRole()
  const updateStatus = useUpdateUserStatus()
  const resetPassword = useResetUserPassword()

  const [roleId, setRoleId] = useState<number | null>(null)
  const [statusId, setStatusId] = useState<number | null>(null)
  const [newPassword, setNewPassword] = useState('')
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null)

  if (isLoading) return <div className="text-sm p-2 text-gray-500">Загрузка данных...</div>
  if (error || !details) return <div className="text-sm p-2 text-red-600">Ошибка загрузки деталей</div>

  const handleUpdateRole = async () => {
    if (!roleId) return
    try {
      await updateRole.mutateAsync({ userId, roleId })
      setMessage({ type: 'success', text: 'Роль обновлена (потребуется повторный вход)' })
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка обновления роли' })
    }
  }

  const handleUpdateStatus = async () => {
    if (!statusId) return
    try {
      await updateStatus.mutateAsync({ userId, statusId })
      setMessage({ type: 'success', text: 'Статус обновлен' })
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка обновления статуса' })
    }
  }

  const handleResetPassword = async () => {
    const password = newPassword.trim()
    if (password.length < 8) {
      setMessage({ type: 'error', text: 'Пароль должен быть не короче 8 символов' })
      return
    }
    try {
      await resetPassword.mutateAsync({ userId, password })
      setNewPassword('')
      setMessage({ type: 'success', text: 'Пароль сброшен. Передайте новый пароль пользователю.' })
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка сброса пароля' })
    }
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-sm">
      <div className="space-y-3">
        <h4 className="font-semibold text-gray-900 border-b pb-1">Профиль</h4>
        <div className="grid grid-cols-3 gap-2">
          <span className="text-gray-500">Имя:</span> <span className="col-span-2 font-medium">{details.fullName || '—'}</span>
          <span className="text-gray-500">Username:</span> <span className="col-span-2 font-medium">@{details.username || '—'}</span>
          <span className="text-gray-500">Email:</span> <span className="col-span-2 font-medium">{details.email}</span>
          <span className="text-gray-500">ID:</span> <span className="col-span-2 font-mono text-xs break-all">{details.id}</span>
          <span className="text-gray-500">Контакты:</span> <span className="col-span-2">{details.contactInfo || '—'}</span>
          <span className="text-gray-500">Био:</span> <span className="col-span-2">{details.bio || '—'}</span>
        </div>

        <h4 className="font-semibold text-gray-900 border-b pb-1 mt-4">Статистика</h4>
        <div className="grid grid-cols-3 gap-2">
          <span className="text-gray-500">Друзья:</span> <span className="col-span-2">{details.friendsCount}</span>
          <span className="text-gray-500">Читатели:</span> <span className="col-span-2">{details.followersCount}</span>
          <span className="text-gray-500">Читает:</span> <span className="col-span-2">{details.followingCount}</span>
        </div>

        <div className="mt-2 flex gap-2">
          <span className="text-gray-500">Психологическая анкета:</span>
          <span className={details.psychProfileFilled ? 'text-green-600 font-medium' : 'text-gray-400'}>
            {details.psychProfileFilled ? 'заполнена' : 'не заполнена'}
          </span>
        </div>
      </div>

      <div className="space-y-3">
        <h4 className="font-semibold text-gray-900 border-b pb-1">Управление</h4>
        
        <div className="bg-white p-3 rounded border space-y-4">
          <div className="flex flex-col gap-1">
            <label className="text-gray-500 text-xs font-medium uppercase tracking-wider">Роль</label>
            <div className="flex items-center gap-2">
              <select 
                value={roleId || details.roleId} 
                onChange={(e) => setRoleId(Number(e.target.value))}
                className="border border-gray-300 rounded px-2 py-1.5 text-sm flex-1"
              >
                {ROLE_OPTIONS.map((opt) => (
                  <option key={opt.id} value={opt.id}>{opt.label}</option>
                ))}
              </select>
              <button 
                onClick={handleUpdateRole}
                disabled={updateRole.isPending || (roleId || details.roleId) === details.roleId}
                className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded text-sm disabled:opacity-50"
              >
                Сохранить
              </button>
            </div>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-gray-500 text-xs font-medium uppercase tracking-wider">Статус</label>
            <div className="flex items-center gap-2">
              <select 
                value={statusId || details.statusId} 
                onChange={(e) => setStatusId(Number(e.target.value))}
                className="border border-gray-300 rounded px-2 py-1.5 text-sm flex-1"
              >
                {STATUS_OPTIONS.map((opt) => (
                  <option key={opt.id} value={opt.id}>{opt.label}</option>
                ))}
              </select>
              <button 
                onClick={handleUpdateStatus}
                disabled={updateStatus.isPending || (statusId || details.statusId) === details.statusId}
                className="bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded text-sm disabled:opacity-50"
              >
                Сохранить
              </button>
            </div>
          </div>

          <div className="flex flex-col gap-1 border-t pt-4">
            <label className="text-gray-500 text-xs font-medium uppercase tracking-wider">Сброс пароля</label>
            <div className="flex items-center gap-2">
              <div className="flex-1 min-w-0">
                <PasswordInput
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Новый пароль (мин. 8 символов)"
                  autoComplete="new-password"
                  className="border-gray-300 rounded px-2 py-1.5 text-sm"
                />
              </div>
              <button
                onClick={handleResetPassword}
                disabled={resetPassword.isPending || newPassword.trim().length < 8}
                className="bg-amber-600 hover:bg-amber-700 text-white px-3 py-1.5 rounded text-sm disabled:opacity-50 whitespace-nowrap"
              >
                Сбросить
              </button>
            </div>
          </div>

          {message && (
            <div className={`text-sm mt-2 p-2 rounded ${message.type === 'error' ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'}`}>
              {message.text}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
