import type { TurnResponse } from '../../features/chat/types'

export const wsUserTurnToResponse = (dto: Record<string, unknown>, roomId: string): TurnResponse => ({
  id: String(dto.id),
  roomId,
  seq: Number(dto.seq),
  role: 'USER',
  participantId: dto.participantId ? String(dto.participantId) : null,
  content: String(dto.content ?? ''),
  promptTokens: null,
  completionTokens: null,
  createdAt: dto.createdAt ? String(dto.createdAt) : new Date().toISOString(),
})

export const wsAssistantTurnToResponse = (dto: Record<string, unknown>, roomId: string): TurnResponse => ({
  id: String(dto.id),
  roomId,
  seq: Number(dto.seq),
  role: 'ASSISTANT',
  participantId: null,
  content: String(dto.content ?? ''),
  promptTokens: null,
  completionTokens: null,
  createdAt: dto.createdAt ? String(dto.createdAt) : new Date().toISOString(),
})
