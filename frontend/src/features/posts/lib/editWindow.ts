import type { UserRole } from '../../../shared/types/api'
import type { Comment } from '../types'

export const EDIT_WINDOW_MS = 15 * 60 * 1000

export function getEditDeadline(comment: Comment): number {
  return Date.parse(comment.createdAt) + EDIT_WINDOW_MS
}

export function canEditComment(
  comment: Comment,
  userId: string | undefined,
  now: number = Date.now(),
): boolean {
  if (!userId || comment.deleted) return false
  if (comment.authorId !== userId) return false
  return now < getEditDeadline(comment)
}

export function canDeleteComment(
  comment: Comment,
  userId: string | undefined,
  role: UserRole | undefined,
): boolean {
  if (!userId || comment.deleted) return false
  if (comment.authorId === userId) return true
  return role === 'ADMIN'
}

export function minutesLeftToEdit(comment: Comment, now: number = Date.now()): number {
  const ms = getEditDeadline(comment) - now
  if (ms <= 0) return 0
  return Math.ceil(ms / 60_000)
}
