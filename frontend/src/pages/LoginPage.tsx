import { Link, Navigate, useLocation } from 'react-router-dom'
import { LoginForm } from '../features/auth/components/LoginForm'
import { useAuthStore } from '../shared/stores/authStore'
import { getSafeRedirect } from '../shared/lib/redirect'

export default function LoginPage() {
  const token = useAuthStore((state) => state.token)
  const location = useLocation()

  if (token) {
    return <Navigate to={getSafeRedirect(location.search)} replace />
  }

  const registerLink = location.search
    ? `/register${location.search}`
    : '/register'

  return (
    <div className="min-h-[70vh] flex items-center justify-center px-4 py-8">
      <main className="w-full max-w-sm p-6 bg-gray-100 rounded border">
        <h1 className="text-2xl font-bold mb-6">Вход</h1>
        <LoginForm />
        <div className="mt-4 text-center">
          <Link to={registerLink} className="text-blue-600 underline text-sm">
            Нет аккаунта? Зарегистрироваться
          </Link>
        </div>
      </main>
    </div>
  )
}
