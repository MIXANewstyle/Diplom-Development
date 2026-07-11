import { Link, useParams } from 'react-router-dom'
import { useRef } from 'react'
import { ProtectedRoute } from '../shared/components/ProtectedRoute'
import { AppShell } from '../shared/layouts/AppShell'
import { GuestRoomLayout } from '../shared/layouts/GuestRoomLayout'
import { RoomPage } from './RoomPage'
import { useAuthStore } from '../shared/stores/authStore'
import { useGuestSessionStore } from '../shared/stores/guestSessionStore'
import { isTokenExpired } from '../shared/lib/jwt'

export const RoomAccessPage = () => {
  const { roomId } = useParams<{ roomId: string }>()
  const id = roomId || ''

  const authToken = useAuthStore((s) => s.token)
  const guestSessionLive = useGuestSessionStore((s) => s.getSession(id))
  // Keep mount-stable credentials so clearing the store on room end does not
  // kick the guest out of the archived dialogue view mid-session.
  const guestCredsRef = useRef(guestSessionLive)
  if (guestSessionLive) guestCredsRef.current = guestSessionLive
  const guestSession = guestSessionLive ?? guestCredsRef.current

  const expiredGuestSession = useGuestSessionStore((s) => {
    const raw = s.getSessionRaw(id)
    if (!raw || !isTokenExpired(raw.token)) return null
    return raw
  })

  const isAuthValid = !!authToken && !isTokenExpired(authToken)

  if (isAuthValid) {
    return (
      <ProtectedRoute>
        <AppShell>
          <RoomPage />
        </AppShell>
      </ProtectedRoute>
    )
  }

  if (guestSession) {
    return (
      <GuestRoomLayout displayName={guestSession.participantDisplayName}>
        <RoomPage />
      </GuestRoomLayout>
    )
  }

  if (expiredGuestSession) {
    return (
      <div className="min-h-[70vh] flex items-center justify-center px-4 py-8">
        <main className="w-full max-w-md p-6 bg-white rounded-lg border shadow-sm text-center space-y-4">
          <h1 className="text-xl font-bold text-gray-900">Гостевая сессия истекла</h1>
          <p className="text-gray-600">
            Время доступа к комнате закончилось. Вернитесь по ссылке-приглашению, чтобы
            присоединиться снова.
          </p>
          <Link
            to={`/invite/${expiredGuestSession.inviteToken}`}
            className="inline-block px-6 py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700"
          >
            Вернуться к приглашению
          </Link>
        </main>
      </div>
    )
  }

  return (
    <div className="min-h-[70vh] flex items-center justify-center px-4 py-8">
      <main className="w-full max-w-md p-6 bg-white rounded-lg border shadow-sm text-center space-y-4">
        <h1 className="text-xl font-bold text-gray-900">Нет доступа к комнате</h1>
        <p className="text-gray-600">
          Войдите в аккаунт или присоединитесь как гость по ссылке-приглашению.
        </p>
        <Link
          to="/login"
          className="inline-block px-6 py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700"
        >
          Войти
        </Link>
      </main>
    </div>
  )
}
