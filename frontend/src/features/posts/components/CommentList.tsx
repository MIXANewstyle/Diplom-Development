import type { AxiosError } from 'axios'
import { useAuthStore } from '../../../shared/stores/authStore'
import { canEngage } from '../../../shared/lib/roles'
import { useRootComments } from '../hooks/useRootComments'
import { useCreateComment } from '../hooks/useCreateComment'
import { getCommentErrorMessage } from '../lib/errorMessage'
import { CommentForm } from './CommentForm'
import { CommentItem } from './CommentItem'

export function CommentList({ postId }: { postId: string }) {
  const user = useAuthStore((s) => s.user)
  const engaged = canEngage(user?.role)

  const createMutation = useCreateComment(postId)
  const {
    data,
    isPending,
    error,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = useRootComments(postId)

  const axiosError = error as AxiosError<{ message?: string }> | null

  const handleSubmit = (content: string) => {
    createMutation.mutate(
      { content },
      {
        onSuccess: () => {
          createMutation.reset()
        },
      },
    )
  }

  const comments = data?.pages.flatMap((p) => p.items) ?? []

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">Комментарии</h2>

      {engaged ? (
        <CommentForm
          submitLabel="Отправить"
          isPending={createMutation.isPending}
          error={
            createMutation.isError
              ? getCommentErrorMessage(createMutation.error, 'create')
              : null
          }
          onSubmit={handleSubmit}
        />
      ) : (
        <p className="text-sm text-gray-500">
          Для написания комментариев нужна подписка уровня BASIC или выше.
        </p>
      )}

      {isPending && (
        <p className="text-gray-500 text-sm">Загрузка комментариев...</p>
      )}

      {axiosError && (
        <p className="text-red-500 text-sm">
          {axiosError.response?.data?.message ?? axiosError.message}
        </p>
      )}

      {!error && !isPending && comments.length === 0 && (
        <p className="text-gray-500 text-sm">
          Комментариев пока нет — будьте первым.
        </p>
      )}

      <div className="space-y-2">
        {comments.map((comment) => (
          <CommentItem
            key={comment.id}
            postId={postId}
            comment={comment}
            isRoot
          />
        ))}
      </div>

      {hasNextPage && (
        <button
          type="button"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="w-full py-2 text-sm font-medium text-blue-600 border border-blue-600 rounded hover:bg-blue-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isFetchingNextPage ? 'Загрузка...' : 'Загрузить ещё'}
        </button>
      )}
    </section>
  )
}
