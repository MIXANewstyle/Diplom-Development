import { useState } from 'react'
import { MyPostsList } from '../features/authoring/components/MyPostsList'
import { PostEditorForm } from '../features/authoring/components/PostEditorForm'
import { useMyPosts } from '../features/authoring/hooks'
import type { MyPost } from '../features/authoring/types'

export function AuthoringPage() {
  const [statusFilter, setStatusFilter] = useState<string | undefined>()
  const { data: posts, isLoading } = useMyPosts(statusFilter)
  const [editingPost, setEditingPost] = useState<MyPost | null>(null)
  const [isCreating, setIsCreating] = useState(false)

  if (isCreating) {
    return (
      <div className="max-w-3xl mx-auto space-y-4">
        <PostEditorForm onClose={() => setIsCreating(false)} />
      </div>
    )
  }

  if (editingPost) {
    return (
      <div className="max-w-3xl mx-auto space-y-4">
        <PostEditorForm initialPost={editingPost} onClose={() => setEditingPost(null)} />
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Студия автора</h1>
        <button
          onClick={() => setIsCreating(true)}
          className="px-4 py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700"
        >
          Создать пост
        </button>
      </div>

      <div className="flex gap-2 border-b pb-2">
        <button
          onClick={() => setStatusFilter(undefined)}
          className={`px-3 py-1 rounded-t border-b-2 ${!statusFilter ? 'border-blue-600 font-bold text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-800'}`}
        >
          Все
        </button>
        <button
          onClick={() => setStatusFilter('1')}
          className={`px-3 py-1 rounded-t border-b-2 ${statusFilter === '1' ? 'border-blue-600 font-bold text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-800'}`}
        >
          Черновики
        </button>
        <button
          onClick={() => setStatusFilter('2')}
          className={`px-3 py-1 rounded-t border-b-2 ${statusFilter === '2' ? 'border-blue-600 font-bold text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-800'}`}
        >
          Опубликованные
        </button>
        <button
          onClick={() => setStatusFilter('3')}
          className={`px-3 py-1 rounded-t border-b-2 ${statusFilter === '3' ? 'border-blue-600 font-bold text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-800'}`}
        >
          Архив
        </button>
      </div>
      
      {isLoading ? (
        <div className="text-gray-500">Загрузка...</div>
      ) : (
        <MyPostsList posts={posts || []} onEdit={setEditingPost} />
      )}
    </div>
  )
}
