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
      clearSession: (roomId) =>
        set((state) => {
          const next = { ...state.sessions }
          delete next[roomId]
          return { sessions: next }
        }),
    }),
    {
      name: 'guest-sessions',
      storage: createJSONStorage(() => sessionStorage),
    }
  )
)
