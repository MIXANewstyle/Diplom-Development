import { useState } from 'react'
import { useRegister } from '../hooks/useRegister'
import { isAxiosError } from 'axios'
import type { ApiErrorResponse } from '../../../shared/types/api'

export function RegisterForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [username, setUsername] = useState('')
  const [fullName, setFullName] = useState('')
  
  const { mutate, isPending, error } = useRegister()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutate({ email, password, username, fullName })
  }

  let errorMessage = ''
  let fieldErrors: Record<string, string> = {}

  if (error) {
    if (isAxiosError<ApiErrorResponse>(error)) {
      errorMessage = error.response?.data?.message ?? error.message
      fieldErrors = error.response?.data?.fieldErrors ?? {}
    } else {
      errorMessage = error.message ?? 'Произошла ошибка'
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div>
        <label className="block text-sm font-medium mb-1">Email</label>
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full border rounded p-2"
        />
        {fieldErrors.email && <p className="text-red-600 text-sm mt-1">{fieldErrors.email}</p>}
      </div>
      <div>
        <label className="block text-sm font-medium mb-1">Имя пользователя (username)</label>
        <input
          type="text"
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="w-full border rounded p-2"
        />
        {fieldErrors.username && <p className="text-red-600 text-sm mt-1">{fieldErrors.username}</p>}
      </div>
      <div>
        <label className="block text-sm font-medium mb-1">Полное имя (Full Name)</label>
        <input
          type="text"
          required
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          className="w-full border rounded p-2"
        />
        {fieldErrors.fullName && <p className="text-red-600 text-sm mt-1">{fieldErrors.fullName}</p>}
      </div>
      <div>
        <label className="block text-sm font-medium mb-1">Пароль</label>
        <input
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full border rounded p-2"
        />
        {fieldErrors.password && <p className="text-red-600 text-sm mt-1">{fieldErrors.password}</p>}
      </div>

      {errorMessage && <p className="text-red-600 font-medium">{errorMessage}</p>}

      <button
        type="submit"
        disabled={isPending}
        className="bg-blue-600 text-white p-2 rounded disabled:opacity-50"
      >
        {isPending ? 'Подождите...' : 'Зарегистрироваться'}
      </button>
    </form>
  )
}
