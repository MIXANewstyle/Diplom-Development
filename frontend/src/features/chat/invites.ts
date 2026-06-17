import { apiClient } from '../../shared/api/client'

export interface MintInviteResponse {
  token: string
  expiresAt: string
}

export interface InviteLandingResponse {
  roomId: string
  hostName: string
  mode: string
  expiresAt: string
}

// Note: the backend reads roomId as a request param (query string), not a body.
export const mintInvite = async (roomId: string): Promise<MintInviteResponse> => {
  const { data } = await apiClient.post<MintInviteResponse>('/api/v1/invites/mint', null, {
    params: { roomId },
  })
  return data
}

export const revokeInvite = async (roomId: string): Promise<void> => {
  await apiClient.post('/api/v1/invites/revoke', null, { params: { roomId } })
}

export const getInviteLanding = async (token: string): Promise<InviteLandingResponse> => {
  const { data } = await apiClient.get<InviteLandingResponse>(
    `/api/v1/invites/${token}/landing`
  )
  return data
}

export const joinInvite = async (token: string): Promise<void> => {
  await apiClient.post(`/api/v1/invites/${token}/join`)
}

export interface GuestJoinRequest {
  displayName: string
  age: number
  gender: string
}

export interface JoinInviteGuestResponse {
  token: string
}

export const joinInviteGuest = async (
  token: string,
  body: GuestJoinRequest
): Promise<JoinInviteGuestResponse> => {
  const { data } = await apiClient.post<JoinInviteGuestResponse>(
    `/api/v1/invites/${token}/join/guest`,
    body
  )
  return data
}

export const buildInviteUrl = (token: string): string =>
  `${window.location.origin}/invite/${token}`
