export type RoomType = 'SOLO' | 'PAIRED'
export type RoomStatus = 'CREATED' | 'WAITING_CONSENT' | 'ACTIVE' | 'ARCHIVED' | 'ABANDONED' | 'EXPIRED'
export type TurnRole = 'USER' | 'ASSISTANT' | 'SYSTEM'

export interface ParticipantResponse {
  id: string
  userId: string
  role: string
  displayName: string
  avatarUrl: string | null
  consentStartAt: string | null
  joinedAt: string | null
  guestDisplayName?: string | null
  guestGenderId?: string | null
  guestAge?: string | null
}

export interface RoomResponse {
  id: string
  title: string | null
  type: RoomType
  status: RoomStatus
  phase: string | null
  currentFloorParticipantId: string | null
  aiModel: string | null
  ownerUserId: string
  createdAt: string
  startedAt: string | null
  participants: ParticipantResponse[]
}

export interface RoomSummaryResponse {
  id: string
  title: string | null
  type: RoomType
  status: RoomStatus
  myRole: string | null
  createdAt: string
  startedAt: string | null
  otherParticipantDisplayName: string | null
  otherParticipantAvatarUrl: string | null
}

export interface TurnResponse {
  id: string
  roomId: string
  seq: number
  role: TurnRole
  participantId: string | null
  content: string
  promptTokens: number | null
  completionTokens: number | null
  createdAt: string
}

export interface SubmitTurnResponse {
  userTurn: TurnResponse
  assistantTurn: TurnResponse
}

export interface TurnsPageResponse {
  items: TurnResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
