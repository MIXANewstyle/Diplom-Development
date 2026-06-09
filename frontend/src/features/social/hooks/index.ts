import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as api from '../api'

export function useMyFollows(userId?: string) {
  return useQuery({
    queryKey: ['social', 'follows', userId],
    queryFn: () => api.getMyFollows(userId!),
    enabled: !!userId,
  })
}

export function useFollow() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.followAuthor,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['social', 'follows'] })
      queryClient.invalidateQueries({ queryKey: ['feed'] })
    },
  })
}

export function useUnfollow() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.unfollowAuthor,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['social', 'follows'] })
      queryClient.invalidateQueries({ queryKey: ['feed'] })
    },
  })
}

export function useFriends() {
  return useQuery({
    queryKey: ['social', 'friends'],
    queryFn: api.getMyFriends,
  })
}

export function useSendFriendRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.sendFriendRequest,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['social', 'friends'] })
    },
  })
}

export function useAcceptFriendRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.acceptFriendRequest,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['social', 'friends'] })
    },
  })
}

export function useDeclineFriendRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.declineFriendRequest,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['social', 'friends'] })
    },
  })
}

export function useCancelFriendRequest() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.cancelFriendRequest,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['social', 'friends'] })
    },
  })
}

export function useUserSearch(username: string) {
  const query = username.trim()
  return useQuery({
    queryKey: ['social', 'search', query],
    queryFn: () => api.searchUsers(query),
    enabled: query.length >= 2,
  })
}

export function useUsersBatch(ids: string[]) {
  return useQuery({
    queryKey: ['users', 'batch', ids],
    queryFn: () => api.getUsersBatch(ids),
    enabled: ids.length > 0,
  })
}
