import { useState } from 'react'
import { MyPostsList } from '../features/authoring/components/MyPostsList'
import { PostEditorForm } from '../features/authoring/components/PostEditorForm'
import { useMyPosts } from '../features/authoring/hooks'
import { MyPost } from '../features/authoring/types'

export function AuthoringPage() {
  const { data: posts, isLoading } = useMyPosts()
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
      
      {isLoading ? (
        <div className="text-gray-500">Загрузка...</div>
      ) : (
        <MyPostsList posts={posts || []} onEdit={setEditingPost} />
      )}
    </div>
  )
}
