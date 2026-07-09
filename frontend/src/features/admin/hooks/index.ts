import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminSearchUsers, getAdminUserDetails, getTags, updateUserRole, updateUserStatus, createTag, deleteTag, listPromos, createPromo, updatePromo, listTransactions, getTransaction, refundTransaction, grantSubscription } from '../api'
import type { PromoCreateRequest, PromoUpdateRequest, AdminTransactionFilters, GrantRequest } from '../types'

export function useTransaction(id: string) {
  return useQuery({
    queryKey: ['admin', 'transaction', id],
    queryFn: () => getTransaction(id),
    enabled: !!id,
  })
}

export function useAdminUserSearch(query: string, page: number, size: number) {
  return useQuery({
    queryKey: ['admin', 'users', 'search', query, page, size],
    queryFn: () => adminSearchUsers(query, page, size),
  })
}

export function useAdminUserDetails(userId: string) {
  return useQuery({
    queryKey: ['admin', 'users', 'details', userId],
    queryFn: () => getAdminUserDetails(userId),
    enabled: !!userId,
  })
}

export function useUpdateUserRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, roleId }: { userId: string; roleId: number }) => updateUserRole(userId, roleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    }
  })
}

export function useUpdateUserStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, statusId }: { userId: string; statusId: number }) => updateUserStatus(userId, statusId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    }
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

export function usePromos() {
  return useQuery({
    queryKey: ['admin', 'promos'],
    queryFn: listPromos,
  })
}

export function useCreatePromo() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: PromoCreateRequest) => createPromo(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'promos'] })
    },
  })
}

export function useUpdatePromo() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: PromoUpdateRequest }) => updatePromo(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'promos'] })
    },
  })
}

export function useTransactions(filters: AdminTransactionFilters) {
  return useQuery({
    queryKey: ['admin', 'transactions', filters],
    queryFn: () => listTransactions(filters),
  })
}

export function useRefundTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: refundTransaction,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'transactions'] })
    },
  })
}

export function useGrantSubscription() {
  return useMutation({
    mutationFn: ({ userId, data }: { userId: string; data: GrantRequest }) => grantSubscription(userId, data),
  })
}
