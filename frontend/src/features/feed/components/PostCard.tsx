import { Link } from 'react-router-dom'
import { useAuthStore } from '../../../shared/stores/authStore'
import { canEngage } from '../../../shared/lib/roles'
import { formatDate } from '../../../shared/lib/format'
import { resolveMediaUrl } from '../../../shared/lib/mediaUrl'
import { useUpvote } from '../hooks/useUpvote'
import type { Post } from '../types'

export function PostCard({ post }: { post: Post }) {
  const user = useAuthStore((s) => s.user)
  const upvote = useUpvote()

  const previewImage = (post.imageUrls && post.imageUrls.length > 0)
    ? post.imageUrls[0]
    : post.coverImageUrl

  const imageCount = post.imageUrls?.length ?? (post.coverImageUrl ? 1 : 0)

  return (
    <article className="border rounded overflow-hidden mb-4 hover:bg-gray-50 transition-colors">
      {/* Feed preview image */}
      {previewImage && (
        <div className="relative w-full aspect-[16/9] bg-gray-100">
          <img
            src={resolveMediaUrl(previewImage) || ''}
            alt={post.title}
            className="w-full h-full object-cover"
          />
          {imageCount > 1 && (
            <span className="absolute top-2 right-2 bg-black/60 text-white text-xs px-2 py-0.5 rounded-full">
              1/{imageCount}
            </span>
          )}
        </div>
      )}

      <div className="p-4 flex flex-col gap-2">
        <div className="flex items-start justify-between gap-3 flex-wrap">
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-semibold">
              <Link to={`/posts/${post.id}`} className="hover:underline break-words">
                {post.title}
              </Link>
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Автор: <Link to={`/authors/${post.authorId}`} className="hover:underline">{post.authorUsername ?? 'Без имени'}</Link> ·{' '}
              {post.publishedAt ? formatDate(post.publishedAt) : 'Дата неизвестна'}
            </p>
          </div>

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
        </div>

        <div className="flex items-center gap-4 mt-1 text-sm text-gray-600">
          <span>👁 {post.viewsCount}</span>
          <span>💬 {post.commentsCount}</span>
          {user?.id === post.authorId ? (
            <span title="Нельзя оценивать свой пост">♥ {post.upvotesCount}</span>
          ) : (
            <button
              type="button"
              onClick={() => upvote.mutate(post.id)}
              disabled={upvote.isPending || !canEngage(user?.role)}
              title={!canEngage(user?.role) ? 'Доступно с подпиской BASIC' : undefined}
              className="hover:text-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              ♥ {post.upvotesCount}
            </button>
          )}
        </div>
      </div>
    </article>
  )
}
