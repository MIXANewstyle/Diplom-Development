import { useAuthStore } from '../../../shared/stores/authStore'
import { canEngage } from '../../../shared/lib/roles'
import { formatDate } from '../../../shared/lib/format'
import { Link } from 'react-router-dom'
import { useUpvote } from '../../feed/hooks/useUpvote'
import { FollowButton } from '../../social/components/FollowButton'
import type { Post, EditorBlock, EditorContent } from '../../feed/types'
import { textareaToEditorContentStr } from '../../authoring/lib/content'
import { ImageCarousel } from './ImageCarousel'
import { renderBlock } from '../lib/markdown'
import React from 'react'


export function PostView({ post, previewMode = false }: { post: Post; previewMode?: boolean }) {
  const user = useAuthStore((s) => s.user)
  const upvote = useUpvote()
  const engaged = canEngage(user?.role)

  // Determine images to display: prefer imageUrls, fall back to legacy coverImageUrl
  const images = (post.imageUrls && post.imageUrls.length > 0)
    ? post.imageUrls
    : (post.coverImageUrl ? [post.coverImageUrl] : [])

  return (
    <article className="space-y-4">
      {images.length > 0 && (
        <ImageCarousel images={images} alt={post.title} />
      )}

      <header className="space-y-2">
        <h1 className="text-3xl font-bold text-gray-900">{post.title}</h1>
        <p className="text-sm text-gray-500 flex items-center gap-2">
          <span>Автор: {previewMode ? (
            <span className="font-medium text-gray-800">{post.authorUsername ?? 'Без имени'}</span>
          ) : (
            <Link to={`/authors/${post.authorId}`} className="hover:underline font-medium text-gray-800">{post.authorUsername ?? 'Без имени'}</Link>
          )}</span>
          {!previewMode && <FollowButton authorId={post.authorId} />}
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
        {previewMode || user?.id === post.authorId ? (
          <span title={previewMode ? "В предпросмотре недоступно" : "Нельзя оценивать свой пост"}>♥ {post.upvotesCount}</span>
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
