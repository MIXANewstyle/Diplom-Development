export type Comment = {
  id: string
  postId: string
  authorId: string
  authorUsername: string | null
  authorAvatarUrl: string | null
  parentId: string | null
  content: string
  deleted: boolean
  createdAt: string
  updatedAt: string
  repliesCount: number
}

export type CommentsResponse = {
  items: Comment[]
  nextCursor: string | null
}

export type CreateCommentBody = {
  content: string
  parentId?: string | null
}

export type UpdateCommentBody = {
  content: string
}
