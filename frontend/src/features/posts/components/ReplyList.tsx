import type { AxiosError } from 'axios'
import { useReplies } from '../hooks/useReplies'
import { CommentItem } from './CommentItem'

export function ReplyList({
  postId,
  rootCommentId,
}: {
  postId: string
  rootCommentId: string
}) {
  const {
    data,
    isPending,
    error,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = useReplies(rootCommentId, { enabled: true })

  if (isPending) {
    return <p className="text-xs text-gray-400">Загрузка ответов...</p>
  }

  const axiosError = error as AxiosError<{ message?: string }> | null
  if (axiosError) {
    return (
      <p className="text-xs text-red-500">
        Не удалось загрузить ответы:{' '}
        {axiosError.response?.data?.message ?? axiosError.message}
      </p>
    )
  }

  const replies = data?.pages.flatMap((p) => p.items) ?? []

  if (replies.length === 0) {
    return <p className="text-xs text-gray-400">Ответов пока нет.</p>
  }

  return (
    <div className="space-y-1 ml-2">
      {replies.map((reply) => (
        <CommentItem
          key={reply.id}
          postId={postId}
          comment={reply}
          isRoot={false}
        />
      ))}
      {hasNextPage && (
        <button
          type="button"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="text-xs text-blue-600 hover:underline disabled:opacity-50"
        >
          {isFetchingNextPage ? 'Загрузка...' : 'Показать ещё ответы'}
        </button>
      )}
    </div>
  )
}
