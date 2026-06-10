import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { searchUsers, getTags, updateUserRole, updateUserStatus, createTag, deleteTag } from '../api'

export function useUserSearch(username: string) {
  return useQuery({
    queryKey: ['admin', 'users', 'search', username],
    queryFn: () => searchUsers(username),
    enabled: username.trim().length >= 2,
  })
}

export function useUpdateUserRole() {
  return useMutation({
    mutationFn: ({ userId, roleId }: { userId: string; roleId: number }) => updateUserRole(userId, roleId),
  })
}

export function useUpdateUserStatus() {
  return useMutation({
    mutationFn: ({ userId, statusId }: { userId: string; statusId: number }) => updateUserStatus(userId, statusId),
  })
}

export function useAdminTags() {
  return useQuery({
    queryKey: ['tags'], // Reuse feed tags queryKey so they stay in sync
    queryFn: getTags,
  })
}

export function useCreateTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createTag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tags'] })
    },
  })
}

export function useDeleteTag() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteTag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tags'] })
    },
  })
}
