import type { MyPost } from '../types'
import { POST_STATUS_MAP } from '../types'
import { usePublishPost, useArchivePost, useDeletePost } from '../hooks'
import { formatDate } from '../../../shared/lib/format'
import { getErrorMessage } from '../../../shared/lib/errors'

export function MyPostsList({ posts, onEdit }: { posts: MyPost[], onEdit: (p: MyPost) => void }) {
  const publishPost = usePublishPost()
  const archivePost = useArchivePost()
  const deletePost = useDeletePost()

  if (posts.length === 0) {
    return <div className="text-gray-500 py-4">У вас пока нет постов.</div>
  }

  const handleError = (err: unknown) => {
    alert(getErrorMessage(err))
  }

  const handleAction = async (action: () => Promise<any>) => {
    try {
      await action()
    } catch (err) {
      handleError(err)
    }
  }

  return (
    <div className="space-y-4">
      {posts.map(post => {
        const isDraft = post.status === 'DRAFT'
        const isPublished = post.status === 'PUBLISHED'
        const isBusy = publishPost.isPending || archivePost.isPending || deletePost.isPending

        return (
          <div key={post.id} className="border p-4 rounded bg-white shadow-sm flex flex-col gap-2">
            <div className="flex justify-between items-start">
              <h3 className="font-bold text-lg">{post.title || 'Без названия'}</h3>
              <span className="text-xs px-2 py-1 bg-gray-100 rounded text-gray-700 font-medium">
                {POST_STATUS_MAP[post.status]}
              </span>
            </div>
            <div className="text-sm text-gray-500">
              Обновлено: {formatDate(post.updatedAt)}
            </div>
            <div className="flex gap-4 mt-2 border-t pt-2">
              <button
                onClick={() => onEdit(post)}
                className="text-sm text-blue-600 hover:text-blue-800 font-medium"
              >
                Редактировать
              </button>
              {isDraft && (
                <button
                  disabled={isBusy}
                  onClick={() => handleAction(() => publishPost.mutateAsync(post.id))}
                  className="text-sm text-green-600 hover:text-green-800 font-medium disabled:opacity-50"
                >
                  Опубликовать
                </button>
              )}
              {isPublished && (
                <button
                  disabled={isBusy}
                  onClick={() => handleAction(() => archivePost.mutateAsync(post.id))}
                  className="text-sm text-yellow-600 hover:text-yellow-800 font-medium disabled:opacity-50"
                >
                  В архив
                </button>
              )}
              {(isDraft || post.status === 'ARCHIVED') && (
                <button
                  disabled={isBusy}
                  onClick={() => {
                    if (window.confirm('Вы уверены, что хотите удалить этот пост?')) {
                      handleAction(() => deletePost.mutateAsync(post.id))
                    }
                  }}
                  className="text-sm text-red-600 hover:text-red-800 font-medium disabled:opacity-50"
                >
                  Удалить
                </button>
              )}
            </div>
          </div>
        )
      })}
    </div>
  )
}
