import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { queryClient } from '../api/queryClient'

export function AppLayout() {
  const { user, clearAuth } = useAuthStore()
  const navigate = useNavigate()

  const handleLogout = () => {
    clearAuth()
    queryClient.clear()
    navigate('/login')
  }

  return (
    <div className="min-h-screen flex flex-col text-gray-700">
      <header className="bg-gray-100 border-b border-gray-300">
        <div className="max-w-5xl mx-auto px-6 h-14 flex items-center justify-between">
          <div className="font-bold text-lg">Mindspace</div>
          
          <nav className="flex gap-6">
            <NavLink 
              to="/feed" 
              className={({ isActive }) => isActive ? 'underline' : ''}
            >
              Лента
            </NavLink>
            <NavLink 
              to="/search" 
              className={({ isActive }) => isActive ? 'underline' : ''}
            >
              Поиск
            </NavLink>
            <NavLink 
              to="/friends" 
              className={({ isActive }) => isActive ? 'underline' : ''}
            >
              Друзья
            </NavLink>
            <NavLink 
              to="/me" 
              className={({ isActive }) => isActive ? 'underline' : ''}
            >
              Профиль
            </NavLink>
            <NavLink 
              to="/subscription" 
              className={({ isActive }) => isActive ? 'underline' : ''}
            >
              Подписка
            </NavLink>
            {user && (user.role === 'AUTHOR' || user.role === 'ADMIN') && (
              <NavLink 
                to="/authoring" 
                className={({ isActive }) => isActive ? 'underline' : ''}
              >
                Мои публикации
              </NavLink>
            )}
            <NavLink 
              to="/chat" 
              className={({ isActive }) => isActive ? 'underline' : ''}
            >
              Чат
            </NavLink>
            {user && user.role === 'ADMIN' && (
              <NavLink 
                to="/admin" 
                className={({ isActive }) => isActive ? 'underline' : ''}
              >
                Админка
              </NavLink>
            )}
          </nav>

          <div className="flex items-center gap-4">
            {user && (
              <div className="text-sm text-right leading-tight">
                <div>{user.email}</div>
                <div className="text-xs text-gray-500">{user.role}</div>
              </div>
            )}
            <button 
              onClick={handleLogout}
              className="px-3 py-1 bg-gray-200 hover:bg-gray-300 rounded text-sm"
            >
              Выйти
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto p-6 w-full flex-1">
        <Outlet />
      </main>
    </div>
  )
}
