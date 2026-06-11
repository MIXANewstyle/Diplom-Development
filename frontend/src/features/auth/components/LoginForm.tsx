import { useState } from 'react'
import { useLogin } from '../hooks/useLogin'
import { getErrorMessage } from '../../../shared/lib/errors'
import { ErrorText } from '../../../shared/components/ErrorText'
import axios from 'axios'

export function LoginForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { mutate, isPending, error } = useLogin()

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    mutate({ email, password })
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

      <ErrorText error={getErrorMessage(error)} className="font-medium text-base" />

      <button
        type="submit"
        disabled={isPending}
        className="bg-blue-600 text-white p-2 rounded disabled:opacity-50"
      >
        {isPending ? 'Подождите...' : 'Войти'}
      </button>
    </form>
  )
}
