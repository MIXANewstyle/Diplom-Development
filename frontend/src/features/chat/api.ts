import { apiClient } from '../../shared/api/client'
import type {
  RoomResponse,
  RoomSummaryResponse,
  SubmitTurnResponse,
  TurnsPageResponse,
} from './types'

export const createSoloRoom = async (): Promise<RoomResponse> => {
  const { data } = await apiClient.post<RoomResponse>('/api/v1/rooms/solo', {
    mode: 'PROBLEM_SOLVING',
  })
  return data
}

export const listRooms = async (page = 0, size = 20): Promise<RoomSummaryResponse[]> => {
  const { data } = await apiClient.get<RoomSummaryResponse[]>('/api/v1/rooms', {
    params: { page, size },
  })
  return data
}

export const getRoom = async (
  roomId: string,
  options?: { authToken?: string }
): Promise<RoomResponse> => {
  const { data } = await apiClient.get<RoomResponse>(`/api/v1/rooms/${roomId}`, {
    headers: options?.authToken
      ? { Authorization: `Bearer ${options.authToken}` }
      : undefined,
  })
  return data
}

export const getTurns = async (
  roomId: string,
  page = 0,
  size = 50,
  options?: { authToken?: string }
): Promise<TurnsPageResponse> => {
  const { data } = await apiClient.get<TurnsPageResponse>(
    `/api/v1/rooms/${roomId}/turns`,
    {
      params: { page, size },
      headers: options?.authToken
        ? { Authorization: `Bearer ${options.authToken}` }
        : undefined,
    }
  )
  return data
}

export const submitTurn = async (
  roomId: string,
  text: string
): Promise<SubmitTurnResponse> => {
  const { data } = await apiClient.post<SubmitTurnResponse>(
    `/api/v1/rooms/${roomId}/turns`,
    { text }
  )
  return data
}

export const endSoloRoom = async (roomId: string): Promise<RoomResponse> => {
  const { data } = await apiClient.post<RoomResponse>(`/api/v1/rooms/${roomId}/end`)
  return data
}

export const createPairedRoom = async (friendUserId: string): Promise<RoomResponse> => {
  const { data } = await apiClient.post<RoomResponse>('/api/v1/rooms/paired', {
    inviteMode: 'FRIEND',
    friendUserId,
  })
  return data
}

export const createPairedLinkRoom = async (): Promise<RoomResponse> => {
  const { data } = await apiClient.post<RoomResponse>('/api/v1/rooms/paired', {
    inviteMode: 'LINK',
  })
  return data
}

export const joinRoom = async (roomId: string): Promise<RoomResponse> => {
  const { data } = await apiClient.post<RoomResponse>(`/api/v1/rooms/${roomId}/join`)
  return data
}
