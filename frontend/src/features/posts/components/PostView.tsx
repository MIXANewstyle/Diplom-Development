import { useAuthStore } from '../../../shared/stores/authStore'
import { canEngage } from '../../../shared/lib/roles'
import { formatDate } from '../../../shared/lib/format'
import { useUpvote } from '../../feed/hooks/useUpvote'
import type { Post } from '../../feed/types'

export function PostView({ post }: { post: Post }) {
  const user = useAuthStore((s) => s.user)
  const upvote = useUpvote()
  const engaged = canEngage(user?.role)

  return (
    <article className="space-y-4">
      {post.coverImageUrl && (
        <img
          src={post.coverImageUrl}
          alt={post.title}
          className="w-full max-h-96 object-cover rounded"
        />
      )}

      <header className="space-y-2">
        <h1 className="text-3xl font-bold text-gray-900">{post.title}</h1>
        <p className="text-sm text-gray-500">
          Автор: {post.authorUsername ?? 'Без имени'} ·{' '}
          {post.publishedAt ? formatDate(post.publishedAt) : 'Черновик'}
        </p>
        {post.tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {post.tags.map((tag) => (
              <span
                key={tag.id}
                className="inline-block bg-gray-200 text-xs rounded px-2 py-1"
              >
                {tag.name}
              </span>
            ))}
          </div>
        )}
      </header>

      {post.content && (
        <div className="whitespace-pre-wrap leading-relaxed text-gray-800">
          {post.content}
        </div>
      )}

      <div className="flex items-center gap-4 text-sm text-gray-600 border-t pt-3">
        <span>👁 {post.viewsCount}</span>
        <span>💬 {post.commentsCount}</span>
        <button
          type="button"
          onClick={() => upvote.mutate(post.id)}
          disabled={upvote.isPending || !engaged}
          title={!engaged ? 'Доступно с подпиской BASIC' : undefined}
          className="hover:text-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          ♥ {post.upvotesCount}
        </button>
      </div>
    </article>
  )
}
