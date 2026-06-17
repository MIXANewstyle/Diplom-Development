import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getInviteLanding,
  joinInvite,
  joinInviteGuest,
  mintInvite,
  revokeInvite,
  type GuestJoinRequest,
} from '../invites'

export const useMintInvite = (roomId: string) =>
  useMutation({
    mutationFn: () => mintInvite(roomId),
  })

export const useRevokeInvite = (roomId: string) =>
  useMutation({
    mutationFn: () => revokeInvite(roomId),
  })

export const useInviteLanding = (token: string | undefined) =>
  useQuery({
    queryKey: ['invite', 'landing', token],
    queryFn: () => getInviteLanding(token as string),
    enabled: !!token,
    retry: false,
  })

export const useJoinInvite = (token: string | undefined) => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => joinInvite(token as string),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chat', 'rooms'] })
    },
  })
}

export const useJoinInviteGuest = (token: string | undefined) =>
  useMutation({
    mutationFn: (body: GuestJoinRequest) => joinInviteGuest(token as string, body),
  })
