import { useAuthStore } from '../../../shared/stores/authStore'
import { canEngage } from '../../../shared/lib/roles'
import { formatDate } from '../../../shared/lib/format'
import { Link } from 'react-router-dom'
import { useUpvote } from '../../feed/hooks/useUpvote'
import type { Post, EditorBlock } from '../../feed/types'

function renderBlock(block: EditorBlock, index: number) {
  switch (block.type) {
    case 'paragraph':
      return (
        <p key={index} className="mb-4">
          {block.data.text}
        </p>
      )
    case 'header': {
      const Tag = `h${block.data.level || 2}` as keyof JSX.IntrinsicElements
      return (
        <Tag key={index} className="font-bold my-4">
          {block.data.text}
        </Tag>
      )
    }
    case 'list': {
      const ListTag = block.data.style === 'ordered' ? 'ol' : 'ul'
      return (
        <ListTag key={index} className={`mb-4 pl-6 ${block.data.style === 'ordered' ? 'list-decimal' : 'list-disc'}`}>
          {block.data.items.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ListTag>
      )
    }
    default:
      return null
  }
}

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
          Автор: <Link to={`/authors/${post.authorId}`} className="hover:underline">{post.authorUsername ?? 'Без имени'}</Link> ·{' '}
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
          {typeof post.content === 'string' ? (
            post.content
          ) : (
            post.content.blocks.map((block, i) => renderBlock(block, i))
          )}
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
