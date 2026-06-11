export type Tag = {
  id: string
  name: string
}

export type PostStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'MODERATED'

export type EditorBlock = 
  | { type: 'paragraph'; data: { text: string } }
  | { type: 'header'; data: { text: string; level: number } }
  | { type: 'list'; data: { style: 'ordered' | 'unordered'; items: string[] } }
  | { type: 'quote'; data: { text: string } }

export type EditorContent = {
  time?: number
  blocks: EditorBlock[]
  version?: string
}

export type Post = {
  id: string
  authorId: string
  authorUsername: string | null
  authorAvatarUrl: string | null
  title: string
  content: EditorContent | string | null
  coverImageUrl: string | null
  status: PostStatus
  publishedAt: string | null
  updatedAt: string
  viewsCount: number
  upvotesCount: number
  commentsCount: number
  tags: Tag[]
  keywords: string[]
  version: number
}

export type SortMode = 'newest' | 'most_liked' | 'most_commented' | 'following'

export type FeedResponse = {
  items: Post[]
  nextCursor: string | null
}

export type UpvoteResponse = {
  upvoted: boolean
  upvotesCount: number
}

export type Page<T> = {
  content: T[]
  totalPages: number
  totalElements: number
  last: boolean
  first: boolean
  size: number
  number: number
  numberOfElements: number
  empty: boolean
}
