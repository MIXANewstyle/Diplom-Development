import { useState, useEffect } from 'react'
import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { queryClient } from '../api/queryClient'

export function AppLayout() {
  const { user, clearAuth } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  // Close mobile menu on route change
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
        <div className="max-w-5xl mx-auto px-4 md:px-6 h-14 flex items-center justify-between">
          <NavLink to="/feed" className="font-bold text-lg shrink-0">Claudium</NavLink>

          {/* Desktop nav — hidden on mobile */}
          <nav className="hidden md:flex gap-6">
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

          {/* Desktop user info + logout — hidden on mobile */}
          <div className="hidden md:flex items-center gap-4">
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

          {/* Mobile hamburger — hidden on desktop */}
          <button
            className="md:hidden p-2 rounded hover:bg-gray-200 transition-colors"
            onClick={() => setMenuOpen((v) => !v)}
            aria-label={menuOpen ? 'Закрыть меню' : 'Открыть меню'}
          >
            {menuOpen ? (
            /* X icon */
            <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          ) : (
            /* Menu / hamburger icon */
            <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          )}
          </button>
        </div>

        {/* Mobile dropdown menu */}
        {menuOpen && (
          <div className="md:hidden bg-gray-100 border-t border-gray-200 px-4">
            <nav className="flex flex-col">
              <NavLink to="/feed" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                Лента
              </NavLink>
              <NavLink to="/search" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                Поиск
              </NavLink>
              <NavLink to="/friends" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                Друзья
              </NavLink>
              <NavLink to="/me" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                Профиль
              </NavLink>
              <NavLink to="/subscription" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                Подписка
              </NavLink>
              {user && (user.role === 'AUTHOR' || user.role === 'ADMIN') && (
                <NavLink to="/authoring" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                  Мои публикации
                </NavLink>
              )}
              <NavLink to="/chat" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                Чат
              </NavLink>
              {user && user.role === 'ADMIN' && (
                <NavLink to="/admin" className={mobileNavLinkClass} onClick={() => setMenuOpen(false)}>
                  Админка
                </NavLink>
              )}
            </nav>

            {/* User info + logout in mobile menu */}
            <div className="py-3 flex items-center justify-between border-t border-gray-200">
              {user && (
                <div className="text-sm leading-tight">
                  <div className="font-medium">{user.email}</div>
                  <div className="text-xs text-gray-500">{user.role}</div>
                </div>
              )}
              <button
                onClick={handleLogout}
                className="px-4 py-2 bg-gray-200 hover:bg-gray-300 rounded text-sm"
              >
                Выйти
              </button>
            </div>
          </div>
        )}
      </header>

      <main className="max-w-5xl mx-auto px-4 md:px-6 py-4 md:py-6 w-full flex-1">
        <Outlet />
      </main>
    </div>
  )
}
