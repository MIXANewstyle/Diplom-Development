import { useState } from 'react'
import { useAuthStore } from '../../../shared/stores/authStore'
import { canEngage } from '../../../shared/lib/roles'
import { formatDate } from '../../../shared/lib/format'
import { resolveMediaUrl } from '../../../shared/lib/mediaUrl'
import {
  canDeleteComment,
  canEditComment,
  minutesLeftToEdit,
} from '../lib/editWindow'
import { getCommentErrorMessage } from '../lib/errorMessage'
import { useUpdateComment } from '../hooks/useUpdateComment'
import { useDeleteComment } from '../hooks/useDeleteComment'
import { useCreateComment } from '../hooks/useCreateComment'
import { CommentForm } from './CommentForm'
import { ReplyList } from './ReplyList'
import type { Comment } from '../types'
import { parseInline } from '../lib/markdown'

export function CommentItem({
  postId,
  comment,
  isRoot,
}: {
  postId: string
  comment: Comment
  isRoot: boolean
}) {
  const user = useAuthStore((s) => s.user)
  const [isEditing, setIsEditing] = useState(false)
  const [isReplying, setIsReplying] = useState(false)
  const [repliesExpanded, setRepliesExpanded] = useState(false)

  const updateMutation = useUpdateComment(postId)
  const deleteMutation = useDeleteComment(postId)
  const replyMutation = useCreateComment(postId)

  const editable = canEditComment(comment, user?.id)
  const deletable = canDeleteComment(comment, user?.id, user?.role)
  const engaged = canEngage(user?.role)

  const handleEditSubmit = (content: string) => {
    updateMutation.mutate(
      { commentId: comment.id, parentId: comment.parentId, body: { content } },
      {
        onSuccess: () => {
          setIsEditing(false)
        },
      },
    )
  }

  const handleReplySubmit = (content: string) => {
    replyMutation.mutate(
      { content, parentId: comment.id },
      {
        onSuccess: () => {
          setIsReplying(false)
          setRepliesExpanded(true)
        },
      },
    )
  }

  const handleDelete = () => {
    if (!confirm('Удалить комментарий?')) return
    deleteMutation.mutate({ commentId: comment.id, parentId: comment.parentId })
  }

  const wasEdited =
    !comment.deleted && comment.updatedAt !== comment.createdAt

  return (
    <div
      className={`border-l-2 ${
        isRoot ? 'border-gray-300' : 'border-gray-200'
      } pl-3 py-2`}
    >
      <div className="flex items-start gap-2">
        <div className="w-8 h-8 rounded-full bg-gray-200 shrink-0 overflow-hidden flex items-center justify-center text-xs text-gray-500">
          {comment.authorAvatarUrl ? (
            <img
              src={resolveMediaUrl(comment.authorAvatarUrl) || ''}
              alt={comment.authorUsername || ''}
              className="w-full h-full object-cover"
            />
          ) : (
            (comment.authorUsername ?? '?').slice(0, 1).toUpperCase()
          )}
        </div>

        <div className="min-w-0 flex-1 space-y-1">
          <div className="text-xs text-gray-500">
            <span className="font-medium text-gray-700">
              {comment.authorUsername ?? 'Без имени'}
            </span>{' '}
            · {formatDate(comment.createdAt)}
            {wasEdited && <span className="text-gray-400"> · ред.</span>}
          </div>

          {isEditing ? (
            <CommentForm
              initialValue={comment.content}
              submitLabel="Сохранить"
              autoFocus
              isPending={updateMutation.isPending}
              error={
                updateMutation.isError
                  ? getCommentErrorMessage(updateMutation.error, 'update')
                  : null
              }
              onSubmit={handleEditSubmit}
              onCancel={() => {
                updateMutation.reset()
                setIsEditing(false)
              }}
            />
          ) : (
            <div
              className={`whitespace-pre-wrap text-sm ${
                comment.deleted ? 'text-gray-400 italic' : 'text-gray-800'
              }`}
            >
              {parseInline(comment.content)}
            </div>
          )}

          {!isEditing && !comment.deleted && (
            <div className="flex flex-wrap items-center gap-3 text-xs text-gray-500">
              {isRoot && engaged && (
                <button
                  type="button"
                  onClick={() => {
                    replyMutation.reset()
                    setIsReplying((v) => !v)
                  }}
                  className="hover:text-blue-600"
                >
                  {isReplying ? 'Отмена ответа' : 'Ответить'}
                </button>
              )}
              {editable && (
                <button
                  type="button"
                  onClick={() => {
                    updateMutation.reset()
                    setIsEditing(true)
                  }}
                  className="hover:text-blue-600"
                  title={`Можно редактировать ещё ${minutesLeftToEdit(comment)} мин`}
                >
                  Редактировать ({minutesLeftToEdit(comment)} мин)
                </button>
              )}
              {deletable && (
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={deleteMutation.isPending}
                  className="hover:text-red-600 disabled:opacity-50"
                >
                  {deleteMutation.isPending ? 'Удаление...' : 'Удалить'}
                </button>
              )}
              {deleteMutation.isError && (
                <span className="text-red-500">
                  {getCommentErrorMessage(deleteMutation.error, 'delete')}
                </span>
              )}
            </div>
          )}

          {isReplying && (
            <div className="mt-2">
              <CommentForm
                submitLabel="Ответить"
                placeholder="Ваш ответ..."
                autoFocus
                isPending={replyMutation.isPending}
                error={
                  replyMutation.isError
                    ? getCommentErrorMessage(replyMutation.error, 'create')
                    : null
                }
                onSubmit={handleReplySubmit}
                onCancel={() => {
                  replyMutation.reset()
                  setIsReplying(false)
                }}
              />
            </div>
          )}

          {isRoot && comment.repliesCount > 0 && (
            <button
              type="button"
              onClick={() => setRepliesExpanded((v) => !v)}
              className="text-xs text-blue-600 hover:underline"
            >
              {repliesExpanded
                ? 'Скрыть ответы'
                : `Показать ответы (${comment.repliesCount})`}
            </button>
          )}

          {isRoot && repliesExpanded && (
            <div className="mt-2">
              <ReplyList postId={postId} rootCommentId={comment.id} />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
