import type { Post, PostStatus } from '../feed/types'

export type MyPost = Post

export interface PostFormValues {
  title: string
  content?: string
  imageUrls?: string[]
  tagIds?: string[]
  keywords?: string[]
}

export const POST_STATUS_MAP: Record<PostStatus, string> = {
  DRAFT: 'Черновик',
  PUBLISHED: 'Опубликован',
  ARCHIVED: 'В архиве',
  MODERATED: 'Заблокирован модератором',
}
