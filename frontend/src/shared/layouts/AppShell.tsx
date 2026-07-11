import { useState, useEffect, type ReactNode } from 'react'
import { NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { queryClient } from '../api/queryClient'

interface Props {
  children: ReactNode
}

export function AppShell({ children }: Props) {
  const { user, clearAuth } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    setMenuOpen(false)
  }, [location.pathname])

  const handleLogout = () => {
    clearAuth()
    queryClient.clear()
    navigate('/login')
  }

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    isActive ? 'underline' : ''

  const mobileNavLinkClass = ({ isActive }: { isActive: boolean }) =>
    `block py-3 border-b border-gray-200 last:border-0 ${isActive ? 'underline font-medium' : ''}`

  return (
    <div className="min-h-screen flex flex-col text-gray-700">
      <header className="bg-gray-100 border-b border-gray-300">
        <div className="max-w-5xl mx-auto px-4 md:px-6 h-14 flex items-center justify-between gap-3">
          <NavLink to="/feed" className="font-bold text-lg shrink-0">Claudium</NavLink>

          <nav className="hidden lg:flex flex-1 min-w-0 justify-center gap-3 xl:gap-5 text-sm whitespace-nowrap">
            <NavLink to="/feed" className={navLinkClass}>Лента</NavLink>
            <NavLink to="/search" className={navLinkClass}>Поиск</NavLink>
            <NavLink to="/friends" className={navLinkClass}>Друзья</NavLink>
            <NavLink to="/me" className={navLinkClass}>Профиль</NavLink>
            <NavLink to="/subscription" className={navLinkClass}>Подписка</NavLink>
            {user && (user.role === 'AUTHOR' || user.role === 'ADMIN') && (
              <NavLink to="/authoring" className={navLinkClass}>Мои публикации</NavLink>
            )}
            <NavLink to="/chat" className={navLinkClass}>Чат</NavLink>
            {user && user.role === 'ADMIN' && (
              <NavLink to="/admin" className={navLinkClass}>Админка</NavLink>
            )}
          </nav>

          <div className="hidden lg:flex items-center gap-3 shrink-0">
            {user && (
              <div className="text-sm text-right leading-tight min-w-0 max-w-[9rem] xl:max-w-[14rem]">
                <div className="truncate">{user.email}</div>
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

          <button
            className="lg:hidden p-2 rounded hover:bg-gray-200 transition-colors"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={menuOpen ? 'Закрыть меню' : 'Открыть меню'}
          >
            {menuOpen ? (
              <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            ) : (
              <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="18" x2="21" y2="18" />
              </svg>
            )}
          </button>
        </div>

        {menuOpen && (
          <div className="lg:hidden bg-gray-100 border-t border-gray-200 px-4">
            <nav className="flex flex-col">
              <NavLink to="/feed" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>Лента</NavLink>
              <NavLink to="/search" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>Поиск</NavLink>
              <NavLink to="/friends" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>Друзья</NavLink>
              <NavLink to="/me" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>Профиль</NavLink>
              <NavLink to="/subscription" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>Подписка</NavLink>
              {user && (user.role === 'AUTHOR' || user.role === 'ADMIN') && (
                <NavLink to="/authoring" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>Мои публикации</NavLink>
              )}
              <NavLink to="/chat" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>Чат</NavLink>
              {user && user.role === 'ADMIN' && (
                <NavLink to="/admin" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>Админка</NavLink>
              )}
            </nav>

            <div className="py-3 flex items-center justify-between gap-3 border-t border-gray-200">
              {user && (
                <div className="text-sm leading-tight min-w-0">
                  <div className="font-medium truncate">{user.email}</div>
                  <div className="text-xs text-gray-500">{user.role}</div>
                </div>
              )}
              <button
                onClick={handleLogout}
                className="shrink-0 px-4 py-2 bg-gray-200 hover:bg-gray-300 rounded text-sm"
              >
                Выйти
              </button>
            </div>
          </div>
        )}
      </header>

      <main className="max-w-5xl mx-auto px-4 md:px-6 py-4 md:py-6 w-full flex-1">
        {children}
      </main>
    </div>
  )
}
