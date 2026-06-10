import { useState } from 'react'
import { useAdminTags, useCreateTag, useDeleteTag } from '../../features/admin/hooks'

export function AdminTagsPage() {
  const { data: tagsData, isLoading, error } = useAdminTags()
  const createTag = useCreateTag()
  const deleteTag = useDeleteTag()

  const [newTagName, setNewTagName] = useState('')
  const [message, setMessage] = useState<{ type: 'success' | 'error', text: string } | null>(null)

  const handleCreateTag = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newTagName.trim()) return

    try {
      await createTag.mutateAsync(newTagName.trim())
      setMessage({ type: 'success', text: `Тег "${newTagName}" успешно создан.` })
      setNewTagName('')
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка при создании тега' })
    }
  }

  const handleDeleteTag = async (tagId: string, tagName: string) => {
    if (!window.confirm(`Вы уверены, что хотите удалить тег "${tagName}"?`)) {
      return
    }

    try {
      await deleteTag.mutateAsync(tagId)
      setMessage({ type: 'success', text: `Тег "${tagName}" успешно удален.` })
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка при удалении тега' })
    }
  }

  return (
    <div className="space-y-6">
      <div className="bg-white p-4 border border-gray-200 rounded">
        <h2 className="text-lg font-bold mb-4">Создать новый тег</h2>
        <form onSubmit={handleCreateTag} className="flex gap-2">
          <input
            type="text"
            value={newTagName}
            onChange={(e) => setNewTagName(e.target.value)}
            placeholder="Название тега (1-50 символов)"
            maxLength={50}
            required
            className="flex-1 border border-gray-300 rounded px-3 py-2"
          />
          <button 
            type="submit" 
            disabled={createTag.isPending || !newTagName.trim()}
            className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50"
          >
            Создать
          </button>
        </form>
        {message && (
          <div className={`mt-2 text-sm ${message.type === 'error' ? 'text-red-600' : 'text-green-600'}`}>
            {message.text}
          </div>
        )}
      </div>

      <div>
        <h2 className="text-lg font-bold mb-4">Существующие теги</h2>
        {isLoading && <div>Загрузка...</div>}
        {error && <div className="text-red-600">Ошибка: {(error as any)?.message}</div>}

        {tagsData && tagsData.content && tagsData.content.length === 0 && (
          <div className="text-gray-500">Теги не найдены.</div>
        )}

        {tagsData && tagsData.content && tagsData.content.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {tagsData.content.map((tag) => (
              <div 
                key={tag.id} 
                className="flex items-center gap-2 bg-gray-100 border border-gray-200 rounded-full px-3 py-1"
              >
                <span>{tag.name}</span>
                <button
                  onClick={() => handleDeleteTag(tag.id, tag.name)}
                  disabled={deleteTag.isPending}
                  className="text-gray-500 hover:text-red-600 focus:outline-none disabled:opacity-50"
                  title="Удалить тег"
                >
                  &times;
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
