import { useState } from 'react'
import { useRegister } from '../hooks/useRegister'
import { getErrorMessage } from '../../../shared/lib/errors'
import { ErrorText } from '../../../shared/components/ErrorText'
import { PasswordInput } from '../../../shared/components/PasswordInput'
import axios from 'axios'

export function RegisterForm() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [username, setUsername] = useState('')
  const [fullName, setFullName] = useState('')
  const [confirmMismatch, setConfirmMismatch] = useState(false)

  const { mutate, isPending, error } = useRegister()

  const mismatchMessage = 'Пароли не совпадают'
  const passwordsMismatch =
    confirmPassword !== '' && confirmPassword !== password
  const showConfirmError = passwordsMismatch || confirmMismatch

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (password !== confirmPassword) {
      setConfirmMismatch(true)
      return
    }
    setConfirmMismatch(false)
    mutate({ email, password, username, fullName })
  }

  let fieldErrors: Record<string, string> = {}

  if (error && axios.isAxiosError(error)) {
    fieldErrors = error.response?.data?.fieldErrors ?? {}
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div>
        <label htmlFor="register-email" className="block text-sm font-medium mb-1">
          Email
        </label>
        <input
          id="register-email"
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="w-full border rounded p-2"
        />
        <ErrorText error={fieldErrors.email} />
      </div>
      <div>
        <label htmlFor="register-username" className="block text-sm font-medium mb-1">
          Имя пользователя (username)
        </label>
        <input
          id="register-username"
          type="text"
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="w-full border rounded p-2"
        />
        <ErrorText error={fieldErrors.username} />
      </div>
      <div>
        <label htmlFor="register-fullname" className="block text-sm font-medium mb-1">
          Полное имя (Full Name)
        </label>
        <input
          id="register-fullname"
          type="text"
          required
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          className="w-full border rounded p-2"
        />
        <ErrorText error={fieldErrors.fullName} />
      </div>
      <div>
        <label htmlFor="register-password" className="block text-sm font-medium mb-1">
          Пароль
        </label>
        <PasswordInput
          id="register-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
        />
        <ErrorText error={fieldErrors.password} />
      </div>
      <div>
        <label htmlFor="register-confirm-password" className="block text-sm font-medium mb-1">
          Повторите пароль
        </label>
        <PasswordInput
          id="register-confirm-password"
          required
          value={confirmPassword}
          onChange={(e) => {
            setConfirmPassword(e.target.value)
            setConfirmMismatch(false)
          }}
          autoComplete="new-password"
        />
        <ErrorText error={showConfirmError ? mismatchMessage : null} />
      </div>

      <ErrorText error={error ? getErrorMessage(error) : null} className="font-medium text-base" />

      <button
        type="submit"
        disabled={isPending || passwordsMismatch}
        className="bg-blue-600 text-white p-2 rounded disabled:opacity-50"
      >
        {isPending ? 'Подождите...' : 'Зарегистрироваться'}
      </button>
    </form>
  )
}
