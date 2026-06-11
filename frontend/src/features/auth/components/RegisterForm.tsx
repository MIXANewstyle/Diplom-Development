import { useState } from 'react'
import { useRegister } from '../hooks/useRegister'
import { getErrorMessage } from '../../../shared/lib/errors'
import { ErrorText } from '../../../shared/components/ErrorText'
import axios from 'axios'

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

  let fieldErrors: Record<string, string> = {}

  if (error && axios.isAxiosError(error)) {
    fieldErrors = error.response?.data?.fieldErrors ?? {}
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
        <ErrorText error={fieldErrors.email} />
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
        <ErrorText error={fieldErrors.username} />
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
        <ErrorText error={fieldErrors.fullName} />
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
        <ErrorText error={fieldErrors.password} />
      </div>

      <ErrorText error={error ? getErrorMessage(error) : null} className="font-medium text-base" />

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
