import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { isAxiosError } from 'axios'
import { useInviteLanding, useJoinInvite } from '../features/chat/hooks/useInvites'
import { GuestJoinForm } from '../features/chat/components/GuestJoinForm'
import { useAuthStore } from '../shared/stores/authStore'
import { getErrorMessage } from '../shared/lib/errors'
import { isTokenExpired } from '../shared/lib/jwt'

export default function InvitePage() {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()
  const authToken = useAuthStore((s) => s.token)
  const isAuthenticated = !!authToken && !isTokenExpired(authToken)

  const { data: landing, isLoading, isError } = useInviteLanding(token)
  const joinMutation = useJoinInvite(token)
  const [joinError, setJoinError] = useState<string | null>(null)

  const returnUrl = `/invite/${token ?? ''}`

  const handleJoin = async () => {
    if (!landing) return
    setJoinError(null)
    try {
      await joinMutation.mutateAsync()
      navigate(`/chat/${landing.roomId}`)
    } catch (e) {
      // If the visitor is already a participant, just take them into the room.
      const message = isAxiosError(e) ? e.response?.data?.message ?? '' : ''
      if (isAxiosError(e) && e.response && e.response.status >= 400 && e.response.status < 500 &&
        message.toLowerCase().includes('already a participant')) {
        navigate(`/chat/${landing.roomId}`)
        return
      }
      setJoinError(getErrorMessage(e))
    }
  }

  const formattedExpiry = landing?.expiresAt
    ? new Date(landing.expiresAt).toLocaleString('ru-RU', {
        day: 'numeric',
        month: 'long',
        hour: '2-digit',
        minute: '2-digit',
      })
    : null

  return (
    <div className="min-h-[80vh] flex items-center justify-center px-4 py-8">
      <main className="w-full max-w-md p-6 bg-white rounded-lg border shadow-sm space-y-6">
        <div className="text-center">
          <Link to="/" className="text-2xl font-bold text-blue-600">
            Диалог
          </Link>
        </div>

        {isLoading && (
          <p className="text-center text-gray-500">Загрузка приглашения...</p>
        )}

        {isError && (
          <div className="text-center space-y-4">
            <p className="text-red-600 font-medium">
              Ссылка недействительна или уже использована. Попросите у собеседника новую ссылку.
            </p>
            <Link
              to="/"
              className="inline-block px-5 py-2 bg-gray-100 text-gray-800 rounded font-medium hover:bg-gray-200"
            >
              На главную
            </Link>
          </div>
        )}

        {landing && (
          <div className="space-y-6">
            <div className="text-center space-y-2">
              <h1 className="text-xl font-bold text-gray-900">
                {landing.hostName} приглашает вас в совместную комнату
              </h1>
              {formattedExpiry && (
                <p className="text-sm text-gray-500">Приглашение действует до {formattedExpiry}</p>
              )}
            </div>

            {isAuthenticated ? (
              <div className="space-y-3">
                <button
                  onClick={handleJoin}
                  disabled={joinMutation.isPending}
                  className="w-full px-6 py-3 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 disabled:opacity-50"
                >
                  {joinMutation.isPending ? 'Подключение...' : 'Присоединиться'}
                </button>
                {joinError && <p className="text-sm text-red-600 text-center">{joinError}</p>}
              </div>
            ) : (
              <div className="space-y-4">
                <Link
                  to={`/login?redirect=${encodeURIComponent(returnUrl)}`}
                  className="block w-full text-center px-6 py-3 bg-blue-600 text-white rounded font-medium hover:bg-blue-700"
                >
                  Войти / Зарегистрироваться
                </Link>

                <div className="border-t pt-4">
                  {token && (
                    <GuestJoinForm inviteToken={token} roomId={landing.roomId} />
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
