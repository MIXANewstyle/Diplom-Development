import { useState } from 'react'
import type { MyPost, PostFormValues } from '../types'
import { useCreatePost, useUpdatePost } from '../hooks'
import { editorContentToTextarea, textareaToEditorContentStr } from '../lib/content'
import { useTags } from '../../feed/hooks/useTags'
import axios from 'axios'

interface Props {
  initialPost?: MyPost
  onClose: () => void
}

export function PostEditorForm({ initialPost, onClose }: Props) {
  const [title, setTitle] = useState(initialPost?.title || '')
  const [body, setBody] = useState(() => editorContentToTextarea(initialPost?.content || null))
  const [coverImageUrl, setCoverImageUrl] = useState(initialPost?.coverImageUrl || '')
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>(initialPost?.tags.map(t => t.id) || [])
  const [keywordsStr, setKeywordsStr] = useState(initialPost?.keywords.join(', ') || '')
  const [errorMsg, setErrorMsg] = useState('')

  const { data: allTags } = useTags()
  const createPost = useCreatePost()
  const updatePost = useUpdatePost()

  const isPending = createPost.isPending || updatePost.isPending

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMsg('')

    if (!title.trim()) {
      setErrorMsg('Название обязательно')
      return
    }
    if (coverImageUrl && !coverImageUrl.startsWith('https://')) {
      setErrorMsg('Ссылка на обложку должна начинаться с https://')
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
      if (axios.isAxiosError(err)) {
        setErrorMsg(err.response?.data?.message || 'Ошибка сохранения')
      } else {
        setErrorMsg('Неизвестная ошибка')
      }
    }
  }

  const toggleTag = (tagId: string) => {
    setSelectedTagIds(prev => 
      prev.includes(tagId) ? prev.filter(id => id !== tagId) : [...prev, tagId]
    )
  }

  return (
    <form onSubmit={handleSave} className="flex flex-col gap-4 bg-white p-6 rounded-lg shadow-sm border">
      <h2 className="text-xl font-bold">{initialPost ? 'Редактировать пост' : 'Новый пост'}</h2>

      {errorMsg && <div className="text-red-500 text-sm bg-red-50 p-3 rounded">{errorMsg}</div>}

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

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium">Обложка (URL)</label>
        <input
          type="text"
          value={coverImageUrl}
          onChange={e => setCoverImageUrl(e.target.value)}
          className="border border-gray-300 p-2 rounded focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
          placeholder="https://..."
        />
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium">Содержание (используйте `# ` для заголовков)</label>
        <textarea
          value={body}
          onChange={e => setBody(e.target.value)}
          className="border border-gray-300 p-2 rounded h-64 font-mono text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
          placeholder="# Заголовок&#10;&#10;Текст абзаца..."
        />
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-sm font-medium">Теги (до 5)</label>
        <div className="flex flex-wrap gap-2 border border-gray-300 p-3 rounded max-h-40 overflow-y-auto bg-gray-50">
          {allTags?.content?.map(tag => (
            <button
              type="button"
              key={tag.id}
              onClick={() => toggleTag(tag.id)}
              className={`px-3 py-1.5 text-xs rounded-full border transition-colors ${
                selectedTagIds.includes(tag.id) ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 hover:bg-gray-100 border-gray-300'
              }`}
            >
              {tag.name}
            </button>
          ))}
          {(!allTags?.content || allTags.content.length === 0) && <span className="text-gray-500 text-sm">Нет доступных тегов</span>}
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

      <div className="flex justify-end gap-3 mt-4">
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-2 border border-gray-300 rounded text-gray-700 font-medium hover:bg-gray-50"
          disabled={isPending}
        >
          Отмена
        </button>
        <button
          type="submit"
          disabled={isPending}
          className="px-4 py-2 bg-blue-600 text-white font-medium rounded hover:bg-blue-700 disabled:opacity-50"
        >
          {isPending ? 'Сохранение...' : 'Сохранить'}
        </button>
      </div>
    </form>
  )
}
