import { Link, Navigate } from 'react-router-dom'
import { RegisterForm } from '../features/auth/components/RegisterForm'
import { useAuthStore } from '../shared/stores/authStore'

export default function RegisterPage() {
  const token = useAuthStore((state) => state.token)

  if (token) {
    return <Navigate to="/" replace />
  }

  return (
    <main className="max-w-md mx-auto mt-12 p-6 bg-gray-100 rounded border">
      <h1 className="text-2xl font-bold mb-6">Регистрация</h1>
      <RegisterForm />
      <div className="mt-4 text-center">
        <Link to="/login" className="text-blue-600 underline text-sm">
          Уже есть аккаунт? Войти
        </Link>
      </div>
    </main>
  )
}
