import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useJoinInviteGuest } from '../hooks/useInvites'
import {
  parseGuestToken,
  useGuestSessionStore,
} from '../../../shared/stores/guestSessionStore'
import { getErrorMessage } from '../../../shared/lib/errors'

const GENDER_OPTIONS = ['Мужской', 'Женский', 'Другой'] as const

interface Props {
  inviteToken: string
  roomId: string
}

export const GuestJoinForm = ({ inviteToken, roomId }: Props) => {
  const navigate = useNavigate()
  const joinGuestMutation = useJoinInviteGuest(inviteToken)
  const setSession = useGuestSessionStore((s) => s.setSession)

  const [displayName, setDisplayName] = useState('')
  const [age, setAge] = useState('')
  const [gender, setGender] = useState<string>(GENDER_OPTIONS[0])
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const validate = (): boolean => {
    const next: Record<string, string> = {}
    const trimmedName = displayName.trim()

    if (!trimmedName) {
      next.displayName = 'Укажите имя'
    } else if (trimmedName.length > 50) {
      next.displayName = 'Имя не должно быть длиннее 50 символов'
    }

    const ageNum = Number(age)
    if (!age.trim()) {
      next.age = 'Укажите возраст'
    } else if (!Number.isInteger(ageNum) || ageNum < 13 || ageNum > 120) {
      next.age = 'Укажите возраст от 13 до 120 лет'
    }

    setFieldErrors(next)
    return Object.keys(next).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!validate()) return

    try {
      const response = await joinGuestMutation.mutateAsync({
        displayName: displayName.trim(),
        age: Number(age),
        gender,
      })

      const parsed = parseGuestToken(response.token)
      if (!parsed || parsed.roomId !== roomId) {
        setError('Не удалось обработать гостевой токен. Попробуйте ещё раз.')
        return
      }

      setSession({
        roomId: parsed.roomId,
        token: response.token,
        participantId: parsed.participantId,
        participantDisplayName: displayName.trim(),
        inviteToken,
      })

      navigate(`/chat/${roomId}`)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-gray-200 p-4 space-y-4 bg-gray-50">
      <p className="text-sm font-medium text-gray-700">Присоединиться как гость</p>

      <div>
        <label htmlFor="guest-name" className="block text-sm font-medium text-gray-700 mb-1">
          Имя
        </label>
        <input
          id="guest-name"
          type="text"
          required
          maxLength={50}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded text-sm bg-white focus:ring-2 focus:ring-blue-500 outline-none"
          placeholder="Как вас представить"
        />
        {fieldErrors.displayName && (
          <p className="text-xs text-red-600 mt-1">{fieldErrors.displayName}</p>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label htmlFor="guest-age" className="block text-sm font-medium text-gray-700 mb-1">
            Возраст
          </label>
          <input
            id="guest-age"
            type="number"
            required
            min={13}
            max={120}
            value={age}
            onChange={(e) => setAge(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded text-sm bg-white focus:ring-2 focus:ring-blue-500 outline-none"
          />
          {fieldErrors.age && (
            <p className="text-xs text-red-600 mt-1">{fieldErrors.age}</p>
          )}
        </div>

        <div>
          <label htmlFor="guest-gender" className="block text-sm font-medium text-gray-700 mb-1">
            Пол
          </label>
          <select
            id="guest-gender"
            value={gender}
            onChange={(e) => setGender(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded text-sm bg-white focus:ring-2 focus:ring-blue-500 outline-none"
          >
            {GENDER_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>
      </div>

      <button
        type="submit"
        disabled={joinGuestMutation.isPending}
        className="w-full px-6 py-2.5 bg-gray-800 text-white rounded font-medium hover:bg-gray-900 disabled:opacity-50"
      >
        {joinGuestMutation.isPending ? 'Подключение...' : 'Присоединиться как гость'}
      </button>

      {error && <p className="text-sm text-red-600 text-center">{error}</p>}
    </form>
  )
}
