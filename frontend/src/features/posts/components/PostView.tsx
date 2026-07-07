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
import { renderTiptapDoc } from '../lib/tiptapRender'

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
            // Three-way shape detection:
            // 1. TipTap JSON doc: { type: 'doc', content: [...] }
            // 2. Legacy Editor.js blocks: { blocks: [...] }
            // 3. Plain text fallback
            let parsed: any = post.content

            if (typeof parsed === 'string') {
              try {
                parsed = JSON.parse(parsed)
              } catch {
                // Not valid JSON — try legacy textarea→blocks conversion as last resort
                try {
                  const contentObj = JSON.parse(textareaToEditorContentStr(parsed)) as EditorContent
                  return contentObj.blocks.map((block: EditorBlock, i: number) => renderBlock(block, i))
                } catch {
                  return <div className="whitespace-pre-wrap leading-relaxed">{post.content as string}</div>
                }
              }
            }

            // TipTap doc shape
            if (parsed && parsed.type === 'doc' && Array.isArray(parsed.content)) {
              return renderTiptapDoc(parsed)
            }

            // Legacy Editor.js blocks shape
            if (parsed && Array.isArray(parsed.blocks)) {
              return (parsed.blocks as EditorBlock[]).map((block, i) => renderBlock(block, i))
            }

            // Final fallback: plain text
            return <div className="whitespace-pre-wrap leading-relaxed">{String(post.content)}</div>
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
