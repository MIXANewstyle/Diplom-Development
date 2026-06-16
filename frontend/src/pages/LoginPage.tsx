import { Link, Navigate } from 'react-router-dom'
import { LoginForm } from '../features/auth/components/LoginForm'
import { useAuthStore } from '../shared/stores/authStore'

export default function LoginPage() {
  const token = useAuthStore((state) => state.token)

  if (token) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="min-h-[70vh] flex items-center justify-center px-4 py-8">
      <main className="w-full max-w-sm p-6 bg-gray-100 rounded border">
        <h1 className="text-2xl font-bold mb-6">Вход</h1>
        <LoginForm />
        <div className="mt-4 text-center">
          <Link to="/register" className="text-blue-600 underline text-sm">
            Нет аккаунта? Зарегистрироваться
          </Link>
        </div>
      </main>
    </div>
  )
}
