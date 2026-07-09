import { useRef, useState } from 'react'
import type { MyPost, PostFormValues } from '../types'
import { useCreatePost, useUpdatePost } from '../hooks'
import { useTags } from '../../feed/hooks/useTags'
import { resolveMediaUrl } from '../../../shared/lib/mediaUrl'
import { getErrorMessage } from '../../../shared/lib/errors'
import { ErrorText } from '../../../shared/components/ErrorText'
import { uploadCoverImage } from '../api'
import { PostBodyEditor } from './PostBodyEditor'

interface Props {
  initialPost?: MyPost
  onClose: () => void
}

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const MAX_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB
const MAX_IMAGES = 10
const MAX_CONTENT_BYTES = 102400 // 100 KB

export function PostEditorForm({ initialPost, onClose }: Props) {
  const [title, setTitle] = useState(initialPost?.title || '')
  const [editorJson, setEditorJson] = useState<object | null>(null)
  const [imageUrls, setImageUrls] = useState<string[]>(initialPost?.imageUrls ?? [])
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>(initialPost?.tags.map(t => t.id) || [])
  const [keywordsStr, setKeywordsStr] = useState(initialPost?.keywords.join(', ') || '')
  const [errorMsg, setErrorMsg] = useState('')
  const [isUploading, setIsUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')

  const fileInputRef = useRef<HTMLInputElement>(null)

  const { data: tagsPage } = useTags()
  const allTags = tagsPage?.content ?? []
  const createPost = useCreatePost()
  const updatePost = useUpdatePost()

  const isPending = createPost.isPending || updatePost.isPending

  // ── Multi-image upload ────────────────────────────────────────────────────
  const handleFilesChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    // Copy before clearing the input — FileList is live and empties when value is reset.
    const selectedFiles = e.target.files ? Array.from(e.target.files) : []
    if (fileInputRef.current) fileInputRef.current.value = ''
    if (selectedFiles.length === 0) return

    setUploadError('')

    const remaining = MAX_IMAGES - imageUrls.length
    if (remaining <= 0) {
      setUploadError(`Максимум ${MAX_IMAGES} изображений.`)
      return
    }

    const filesToUpload = selectedFiles.slice(0, remaining)
    if (selectedFiles.length > remaining) {
      setUploadError(`Можно добавить ещё ${remaining}. Лишние файлы пропущены.`)
    }

    // Client-side pre-checks
    for (const file of filesToUpload) {
      if (!ACCEPTED_TYPES.includes(file.type)) {
        setUploadError('Допустимые форматы: JPEG, PNG, WebP, GIF.')
        return
      }
      if (file.size > MAX_SIZE_BYTES) {
        setUploadError(`Файл «${file.name}» слишком большой. Максимум 5 МБ.`)
        return
      }
    }

    setIsUploading(true)
    try {
      const uploaded: string[] = []
      for (const file of filesToUpload) {
        const url = await uploadCoverImage(file)
        uploaded.push(url)
      }
      setImageUrls(prev => [...prev, ...uploaded])
    } catch (err) {
      setUploadError(getErrorMessage(err))
    } finally {
      setIsUploading(false)
    }
  }

  const handleRemoveImage = (index: number) => {
    setImageUrls(prev => prev.filter((_, i) => i !== index))
  }

  const handleMoveImage = (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= imageUrls.length) return
    setImageUrls(prev => {
      const next = [...prev]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
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

    const contentStr = editorJson ? JSON.stringify(editorJson) : ''

    // 100 KB guard
    if (new Blob([contentStr]).size > MAX_CONTENT_BYTES) {
      setErrorMsg('Содержание поста превышает допустимый размер (100 КБ). Пожалуйста, сократите текст.')
      return
    }

    const payload: PostFormValues = {
      title: title.trim(),
      content: contentStr || undefined,
      imageUrls: imageUrls,
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

      {/* ── Multi-image uploader ───────────────────────────────────────────── */}
      <div className="flex flex-col gap-2">
        <label className="text-sm font-medium">Изображения поста</label>
        <p className="text-xs text-gray-500">Первое изображение — обложка поста</p>

        {/* Thumbnails grid */}
        {imageUrls.length > 0 && (
          <div className="flex flex-wrap gap-3">
            {imageUrls.map((url, i) => (
              <div
                key={`${url}-${i}`}
                className={`relative group w-24 h-24 rounded border-2 overflow-hidden flex-shrink-0 ${
                  i === 0 ? 'border-blue-500' : 'border-gray-200'
                }`}
              >
                <img
                  src={resolveMediaUrl(url) || ''}
                  alt={`Изображение ${i + 1}`}
                  className="w-full h-full object-cover"
                />
                {i === 0 && (
                  <span className="absolute bottom-0 left-0 right-0 bg-blue-600 text-white text-[10px] text-center py-0.5 leading-none">
                    Обложка
                  </span>
                )}
                {/* Controls overlay */}
                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-1">
                  <button
                    type="button"
                    onClick={() => handleMoveImage(i, -1)}
                    disabled={i === 0}
                    className="w-6 h-6 bg-white rounded text-xs font-bold disabled:opacity-30"
                    title="Сдвинуть влево"
                  >
                    ◀
                  </button>
                  <button
                    type="button"
                    onClick={() => handleRemoveImage(i)}
                    className="w-6 h-6 bg-red-500 text-white rounded text-xs font-bold"
                    title="Удалить"
                  >
                    ✕
                  </button>
                  <button
                    type="button"
                    onClick={() => handleMoveImage(i, 1)}
                    disabled={i === imageUrls.length - 1}
                    className="w-6 h-6 bg-white rounded text-xs font-bold disabled:opacity-30"
                    title="Сдвинуть вправо"
                  >
                    ▶
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Upload button + hidden file input */}
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={isUploading || imageUrls.length >= MAX_IMAGES}
            className="px-4 py-2 text-sm border border-gray-300 rounded font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isUploading ? 'Загрузка...' : 'Добавить изображения'}
          </button>
          <span className="text-xs text-gray-400">
            JPEG, PNG, WebP, GIF · до 5 МБ · {imageUrls.length}/{MAX_IMAGES}
          </span>
        </div>

        {/* Hidden file input */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          multiple
          className="hidden"
          onChange={handleFilesChange}
        />

        {/* Upload-specific error */}
        {uploadError && (
          <p className="text-sm text-red-600">{uploadError}</p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium">Содержание</label>
        
        <PostBodyEditor
          initialContent={initialPost?.content || null}
          onChange={setEditorJson}
        />
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
