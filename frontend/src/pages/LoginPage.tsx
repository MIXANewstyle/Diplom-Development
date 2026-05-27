import { Link, Navigate } from 'react-router-dom'
import { LoginForm } from '../features/auth/components/LoginForm'
import { useAuthStore } from '../shared/stores/authStore'

export default function LoginPage() {
  const token = useAuthStore((state) => state.token)

  if (token) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="max-w-md mx-auto mt-12 p-6 bg-gray-100 rounded border">
      <h1 className="text-2xl font-bold mb-6">Вход</h1>
      <LoginForm />
      <div className="mt-4 text-center">
        <Link to="/register" className="text-blue-600 underline text-sm">
          Нет аккаунта? Зарегистрироваться
        </Link>
      </div>
    </main>
  )
}
