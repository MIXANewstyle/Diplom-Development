export type RoomType = 'SOLO' | 'PAIRED'
export type RoomStatus = 'ACTIVE' | 'ARCHIVED' | 'ABANDONED' | 'EXPIRED'
export type TurnRole = 'USER' | 'ASSISTANT' | 'SYSTEM'

export interface ParticipantResponse {
  id: string
  userId: string
  role: string
  displayName: string
  avatarUrl: string | null
  joinedAt: string
}

export interface RoomResponse {
  id: string
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
