import { NavLink, Outlet } from 'react-router-dom'

export function AdminLayout() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Панель администратора</h1>
      <nav className="flex gap-4 border-b border-gray-300 pb-2">
        <NavLink 
          to="/admin/users" 
          className={({ isActive }) => isActive ? 'font-bold text-blue-600 border-b-2 border-blue-600' : 'text-gray-600 hover:text-gray-900'}
        >
          Пользователи
        </NavLink>
        <NavLink 
          to="/admin/tags" 
          className={({ isActive }) => isActive ? 'font-bold text-blue-600 border-b-2 border-blue-600' : 'text-gray-600 hover:text-gray-900'}
        >
          Теги
        </NavLink>
      </nav>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
