import { useAuthStore } from '../../../shared/stores/authStore'
import { canEngage } from '../../../shared/lib/roles'
import { formatDate } from '../../../shared/lib/format'
import { Link } from 'react-router-dom'
import { useUpvote } from '../../feed/hooks/useUpvote'
import { FollowButton } from '../../social/components/FollowButton'
import type { Post, EditorBlock, EditorContent } from '../../feed/types'
import { textareaToEditorContentStr } from '../../authoring/lib/content'
import React from 'react'

function parseInline(text: string) {
  const parts = text.split(/(\*\*.*?\*\*)/g)
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length >= 4) {
      return <strong key={i}>{part.slice(2, -2)}</strong>
    }
    return <React.Fragment key={i}>{part}</React.Fragment>
  })
}

function renderBlock(block: EditorBlock, index: number) {
  switch (block.type) {
    case 'paragraph':
      return (
        <p key={index} className="mb-3 leading-relaxed">
          {parseInline(block.data.text)}
        </p>
      )
    case 'header': {
      const level = block.data.level || 2
      const classes = 
        level === 1 ? 'text-2xl font-bold mt-6 mb-3' :
        level === 2 ? 'text-xl font-semibold mt-5 mb-2' :
        'text-lg font-semibold mt-4 mb-2'
      const Tag = `h${Math.min(Math.max(level, 1), 6)}` as keyof JSX.IntrinsicElements
      return (
        <Tag key={index} className={classes}>
          {parseInline(block.data.text)}
        </Tag>
      )
    }
    case 'list': {
      const ListTag = block.data.style === 'ordered' ? 'ol' : 'ul'
      return (
        <ListTag key={index} className={`pl-6 mb-3 space-y-1 ${block.data.style === 'ordered' ? 'list-decimal' : 'list-disc'}`}>
          {block.data.items.map((item, i) => (
            <li key={i}>{parseInline(item)}</li>
          ))}
        </ListTag>
      )
    }
    case 'quote':
      return (
        <blockquote key={index} className="border-l-4 pl-4 italic text-gray-600 mb-3 whitespace-pre-wrap">
          {parseInline(block.data.text)}
        </blockquote>
      )
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
        <p className="text-sm text-gray-500 flex items-center gap-2">
          <span>Автор: <Link to={`/authors/${post.authorId}`} className="hover:underline font-medium text-gray-800">{post.authorUsername ?? 'Без имени'}</Link></span>
          <FollowButton authorId={post.authorId} />
          <span>· {post.publishedAt ? formatDate(post.publishedAt) : 'Черновик'}</span>
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
        <div className="text-gray-800">
          {(() => {
            let blocks: EditorBlock[] = []
            if (typeof post.content === 'string') {
              try {
                // If it's valid JSON, it might already be blocks
                const parsed = JSON.parse(post.content) as EditorContent
                if (parsed.blocks) blocks = parsed.blocks
              } catch {
                // Otherwise treat as plain string and parse it now
                try {
                  const contentObj = JSON.parse(textareaToEditorContentStr(post.content)) as EditorContent
                  blocks = contentObj.blocks
                } catch {
                  // Fallback
                  return <div className="whitespace-pre-wrap leading-relaxed">{post.content}</div>
                }
              }
            } else if (post.content.blocks) {
              blocks = post.content.blocks
            }
            return blocks.map((block, i) => renderBlock(block, i))
          })()}
        </div>
      )}

      <div className="flex items-center gap-4 text-sm text-gray-600 border-t pt-3">
        <span>👁 {post.viewsCount}</span>
        <span>💬 {post.commentsCount}</span>
        {user?.id === post.authorId ? (
          <span title="Нельзя оценивать свой пост">♥ {post.upvotesCount}</span>
        ) : (
          <button
            type="button"
            onClick={() => upvote.mutate(post.id)}
            disabled={upvote.isPending || !engaged}
            title={!engaged ? 'Доступно с подпиской BASIC' : undefined}
            className="hover:text-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            ♥ {post.upvotesCount}
          </button>
        )}
      </div>
    </article>
  )
}
