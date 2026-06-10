import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import * as api from '../api'
import { PostFormValues } from '../types'

export function useMyPosts(status?: string) {
  return useQuery({
    queryKey: ['authoring', 'mine', status],
    queryFn: () => api.getMyPosts(status),
  })
}

export function useCreatePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.createPost,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'mine'] })
    },
  })
}

export function useUpdatePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ postId, body }: { postId: string; body: PostFormValues }) =>
      api.updatePost(postId, body),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'mine'] })
      queryClient.invalidateQueries({ queryKey: ['post', data.id] })
      queryClient.invalidateQueries({ queryKey: ['feed'] })
    },
  })
}

export function usePublishPost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.publishPost,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'mine'] })
      queryClient.invalidateQueries({ queryKey: ['post', data.id] })
      queryClient.invalidateQueries({ queryKey: ['feed'] })
    },
  })
}

export function useArchivePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.archivePost,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'mine'] })
      queryClient.invalidateQueries({ queryKey: ['post', data.id] })
      queryClient.invalidateQueries({ queryKey: ['feed'] })
    },
  })
}

export function useDeletePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: api.deletePost,
    onSuccess: (_, postId) => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'mine'] })
      queryClient.invalidateQueries({ queryKey: ['post', postId] })
      queryClient.invalidateQueries({ queryKey: ['feed'] })
    },
  })
}
