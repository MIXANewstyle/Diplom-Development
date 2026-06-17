import { useRef, useState } from 'react'
import { Bold, Underline, Strikethrough } from 'lucide-react'
import type { MyPost, PostFormValues } from '../types'
import { useCreatePost, useUpdatePost } from '../hooks'
import { editorContentToTextarea, textareaToEditorContentStr } from '../lib/content'
import { wrapSelectionWithMark, type InlineMark } from '../lib/formatting'
import { useTags } from '../../feed/hooks/useTags'
import { getErrorMessage } from '../../../shared/lib/errors'
import { ErrorText } from '../../../shared/components/ErrorText'
import { PostView } from '../../posts/components/PostView'
import type { Post } from '../../feed/types'
import { uploadCoverImage } from '../api'

interface Props {
  initialPost?: MyPost
  onClose: () => void
}

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const MAX_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB

export function PostEditorForm({ initialPost, onClose }: Props) {
  const [title, setTitle] = useState(initialPost?.title || '')
  const [body, setBody] = useState(() => editorContentToTextarea(initialPost?.content || null))
  const [coverImageUrl, setCoverImageUrl] = useState(initialPost?.coverImageUrl || '')
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>(initialPost?.tags.map(t => t.id) || [])
  const [keywordsStr, setKeywordsStr] = useState(initialPost?.keywords.join(', ') || '')
  const [errorMsg, setErrorMsg] = useState('')
  const [showPreview, setShowPreview] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')

  const fileInputRef = useRef<HTMLInputElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const [selection, setSelection] = useState<{ start: number; end: number } | null>(null)

  const { data: tagsPage } = useTags()
  const allTags = tagsPage?.content ?? []
  const createPost = useCreatePost()
  const updatePost = useUpdatePost()

  const isPending = createPost.isPending || updatePost.isPending

  // ── Cover upload ──────────────────────────────────────────────────────────
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    // Reset input so the same file can be re-selected after clearing
    if (fileInputRef.current) fileInputRef.current.value = ''
    if (!file) return

    setUploadError('')

    // Client-side pre-checks (backend enforces too; these are UX only)
    if (!ACCEPTED_TYPES.includes(file.type)) {
      setUploadError('Допустимые форматы: JPEG, PNG, WebP, GIF.')
      return
    }
    if (file.size > MAX_SIZE_BYTES) {
      setUploadError('Файл слишком большой. Максимум 5 МБ.')
      return
    }

    setIsUploading(true)
    try {
      const url = await uploadCoverImage(file)
      setCoverImageUrl(url)
    } catch (err) {
      setUploadError(getErrorMessage(err))
    } finally {
      setIsUploading(false)
    }
  }

  const handleRemoveCover = () => {
    setCoverImageUrl('')
    setUploadError('')
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  // ── Form submit ───────────────────────────────────────────────────────────
  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg('')

    if (!title.trim()) {
      setErrorMsg('Название обязательно')
      return
    }
    if (selectedTagIds.length > 5) {
      setErrorMsg('Максимум 5 тегов')
      return
    }

    const keywords = keywordsStr.split(',').map(k => k.trim()).filter(k => k.length > 0)
    if (keywords.length > 20) {
      setErrorMsg('Максимум 20 ключевых слов')
      return
    }
    if (keywords.some(k => k.length > 50)) {
      setErrorMsg('Длина ключевого слова не должна превышать 50 символов')
      return
    }

    const payload: PostFormValues = {
      title: title.trim(),
      content: textareaToEditorContentStr(body),
      coverImageUrl: coverImageUrl.trim() || undefined,
      tagIds: selectedTagIds,
      keywords,
    }

    try {
      if (initialPost) {
        await updatePost.mutateAsync({ postId: initialPost.id, body: payload })
      } else {
        await createPost.mutateAsync(payload)
      }
      onClose()
    } catch (err: unknown) {
      setErrorMsg(getErrorMessage(err))
    }
  }

  const toggleTag = (tagId: string) => {
    setSelectedTagIds(prev =>
      prev.includes(tagId) ? prev.filter(id => id !== tagId) : [...prev, tagId]
    )
  }

  const isSubmitDisabled = isPending || isUploading

  const updateSelection = () => {
    const el = textareaRef.current
    if (!el) return
    const { selectionStart, selectionEnd } = el
    if (selectionStart !== selectionEnd) {
      setSelection({ start: selectionStart, end: selectionEnd })
    } else {
      setSelection(null)
    }
  }

  const applyFormat = (mark: InlineMark) => {
    if (!selection) return
    const { text: newText, newStart, newEnd } = wrapSelectionWithMark(
      body,
      selection.start,
      selection.end,
      mark,
    )
    setBody(newText)
    setSelection({ start: newStart, end: newEnd })
    requestAnimationFrame(() => {
      const el = textareaRef.current
      if (!el) return
      el.focus()
      el.setSelectionRange(newStart, newEnd)
    })
  }

  return (
    <form onSubmit={handleSave} className="flex flex-col gap-4 bg-white p-6 rounded-lg shadow-sm border">
      <h2 className="text-xl font-bold">{initialPost ? 'Редактировать пост' : 'Новый пост'}</h2>

      <ErrorText error={errorMsg} className="bg-red-50 p-3 rounded mt-0" />

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium">Название *</label>
        <input
          type="text"
          value={title}
          onChange={e => setTitle(e.target.value)}
          className="border border-gray-300 p-2 rounded focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
          placeholder="Название поста"
        />
      </div>

      {/* ── Cover image uploader ─────────────────────────────────────────── */}
      <div className="flex flex-col gap-2">
        <label className="text-sm font-medium">Обложка</label>

        {/* Preview of current cover (uploaded or pre-existing https://) */}
        {coverImageUrl && (
          <div className="relative">
            <img
              src={coverImageUrl}
              alt="Предпросмотр обложки"
              className="w-full h-auto max-h-60 object-contain rounded border border-gray-200 bg-gray-50"
            />
            <button
              type="button"
              onClick={handleRemoveCover}
              className="absolute top-2 right-2 bg-white text-gray-700 border border-gray-300 rounded px-2 py-0.5 text-xs font-medium hover:bg-gray-100 shadow-sm"
            >
              Убрать
            </button>
          </div>
        )}

        {/* Upload button + hidden file input */}
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={isUploading}
            className="px-4 py-2 text-sm border border-gray-300 rounded font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isUploading ? 'Загрузка...' : coverImageUrl ? 'Заменить' : 'Загрузить обложку'}
          </button>
          <span className="text-xs text-gray-400">JPEG, PNG, WebP, GIF · до 5 МБ</span>
        </div>

        {/* Hidden file input */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          className="hidden"
          onChange={handleFileChange}
        />

        {/* Upload-specific error */}
        {uploadError && (
          <p className="text-sm text-red-600">{uploadError}</p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <div className="flex justify-between items-end mb-1">
          <label className="text-sm font-medium">Содержание</label>
          <button
            type="button"
            onClick={() => setShowPreview(!showPreview)}
            className="text-sm text-blue-600 hover:text-blue-800 font-medium"
          >
            {showPreview ? 'Редактировать' : 'Предпросмотр'}
          </button>
        </div>
        {!showPreview && (
          <div className="text-xs text-gray-500 mb-1">
            Поддерживается: <code># Заголовок · ## Подзаголовок · - список · 1. нумерация · &gt; цитата · **жирный** · __подчёркнутый__ · ~~зачёркнутый~~</code>
          </div>
        )}
        
        {showPreview ? (
          <div className="border border-gray-300 p-4 rounded min-h-[16rem] bg-gray-50 overflow-y-auto">
            <PostView post={{
              id: 'preview',
              authorId: 'preview',
              authorUsername: 'Автор',
              authorAvatarUrl: null,
              title: title.trim() || 'Без названия',
              content: textareaToEditorContentStr(body),
              coverImageUrl: coverImageUrl || null,
              status: 'DRAFT',
              publishedAt: null,
              updatedAt: new Date().toISOString(),
              viewsCount: 0,
              upvotesCount: 0,
              commentsCount: 0,
              tags: allTags.filter(t => selectedTagIds.includes(t.id)),
              keywords: keywordsStr.split(',').map(k => k.trim()).filter(Boolean),
              version: 1
            } as Post} />
          </div>
        ) : (
          <div className="relative">
            {selection && (
              <div className="flex items-center gap-1 mb-2 p-1 bg-white border border-gray-300 rounded shadow-sm w-fit">
                <button
                  type="button"
                  title="Жирный"
                  onMouseDown={e => e.preventDefault()}
                  onClick={() => applyFormat('bold')}
                  className="p-1.5 rounded hover:bg-gray-100 text-gray-700"
                >
                  <Bold className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  title="Подчёркнутый"
                  onMouseDown={e => e.preventDefault()}
                  onClick={() => applyFormat('underline')}
                  className="p-1.5 rounded hover:bg-gray-100 text-gray-700"
                >
                  <Underline className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  title="Зачёркнутый"
                  onMouseDown={e => e.preventDefault()}
                  onClick={() => applyFormat('strikethrough')}
                  className="p-1.5 rounded hover:bg-gray-100 text-gray-700"
                >
                  <Strikethrough className="w-4 h-4" />
                </button>
              </div>
            )}
            <textarea
              ref={textareaRef}
              value={body}
              onChange={e => setBody(e.target.value)}
              onSelect={updateSelection}
              onMouseUp={updateSelection}
              onKeyUp={updateSelection}
              className="border border-gray-300 p-2 rounded h-64 w-full font-mono text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
              placeholder={"# Заголовок\n\nТекст абзаца..."}
            />
          </div>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium">Теги (до 5)</label>
        <div className="flex flex-wrap gap-2 border border-gray-300 p-3 rounded max-h-40 overflow-y-auto bg-gray-50">
          {allTags.map(tag => (
            <button
              type="button"
              key={tag.id}
              onClick={() => toggleTag(tag.id)}
              className={`px-3 py-1.5 text-xs rounded-full border transition-colors ${selectedTagIds.includes(tag.id) ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 hover:bg-gray-100 border-gray-300'
                }`}
            >
              {tag.name}
            </button>
          ))}
          {allTags.length === 0 && <span className="text-gray-500 text-sm">Нет доступных тегов</span>}
        </div>
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium">Ключевые слова (через запятую, до 20)</label>
        <input
          type="text"
          value={keywordsStr}
          onChange={e => setKeywordsStr(e.target.value)}
          className="border border-gray-300 p-2 rounded focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
          placeholder="психология, терапия"
        />
      </div>

      <div className="flex flex-wrap justify-end gap-3 mt-4">
        <button
          type="button"
          onClick={onClose}
          className="w-full sm:w-auto px-4 py-2 border border-gray-300 rounded text-gray-700 font-medium hover:bg-gray-50"
          disabled={isSubmitDisabled}
        >
          Отмена
        </button>
        <button
          type="submit"
          disabled={isSubmitDisabled}
          className="w-full sm:w-auto px-4 py-2 bg-blue-600 text-white font-medium rounded hover:bg-blue-700 disabled:opacity-50"
        >
          {isPending ? 'Сохранение...' : isUploading ? 'Загрузка...' : 'Сохранить'}
        </button>
      </div>
    </form>
  )
}
