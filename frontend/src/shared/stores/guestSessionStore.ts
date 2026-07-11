import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { isTokenExpired, parseJwt } from '../lib/jwt'

export interface GuestSession {
  roomId: string
  token: string
  participantId: string
  participantDisplayName: string
  inviteToken: string
}

interface GuestSessionState {
  sessions: Record<string, GuestSession>
  setSession: (session: GuestSession) => void
  getSession: (roomId: string) => GuestSession | null
  getSessionRaw: (roomId: string) => GuestSession | null
  /**
   * Re-entry via the same invite link (same browser). Returns a non-expired session
   * whose inviteToken matches; clears a matching but expired entry and returns null.
   * Cross-device recovery (initiator-issued reconnect link bound to the same
   * participantId) is future work — do not re-mint a new guest identity here.
   */
  findSessionByInviteToken: (inviteToken: string) => GuestSession | null
  clearSession: (roomId: string) => void
}

export function parseGuestToken(token: string): { participantId: string; roomId: string } | null {
  const payload = parseJwt<{ sub?: string; aud?: string; scope?: string }>(token)
  if (!payload?.sub || !payload.aud?.startsWith('room:')) {
    return null
  }
  return {
    participantId: payload.sub,
    roomId: payload.aud.replace('room:', ''),
  }
}

export const useGuestSessionStore = create<GuestSessionState>()(
  persist(
    (set, get) => ({
      sessions: {},
      setSession: (session) =>
        set((state) => ({
          sessions: { ...state.sessions, [session.roomId]: session },
        })),
      getSession: (roomId) => {
        const session = get().sessions[roomId]
        if (!session || isTokenExpired(session.token)) {
          return null
        }
        return session
      },
      getSessionRaw: (roomId) => get().sessions[roomId] ?? null,
      findSessionByInviteToken: (inviteToken) => {
        const match = Object.values(get().sessions).find((s) => s.inviteToken === inviteToken)
        if (!match) return null
        if (isTokenExpired(match.token)) {
          get().clearSession(match.roomId)
          return null
        }
        return match
      },
      clearSession: (roomId) =>
        set((state) => {
          const next = { ...state.sessions }
          delete next[roomId]
          return { sessions: next }
        }),
    }),
    {
      name: 'guest-sessions',
      // localStorage so the guest JWT survives tab close / browser restart.
      // sessionStorage was dropping the only client-side copy of the token.
      storage: createJSONStorage(() => localStorage),
    }
  )
)
