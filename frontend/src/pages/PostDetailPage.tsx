import { useParams, Link } from 'react-router-dom'
import type { AxiosError } from 'axios'
import { usePost } from '../features/posts/hooks/usePost'
import { PostView } from '../features/posts/components/PostView'
import { CommentList } from '../features/posts/components/CommentList'

export function PostDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: post, isPending, error } = usePost(id)

  if (!id) {
    return <p className="text-red-500">Некорректный идентификатор поста.</p>
  }

  if (isPending) return <p className="text-gray-500">Загрузка поста...</p>

  if (error) {
    const axiosError = error as AxiosError<{ message?: string }>
    if (axiosError.response?.status === 404) {
      return (
        <div className="space-y-3">
          <p className="text-gray-700">Пост не найден или недоступен.</p>
          <Link to="/feed" className="text-blue-600 hover:underline text-sm">
            Вернуться в ленту
          </Link>
        </div>
      )
    }
    return (
      <p className="text-red-500">
        {axiosError.response?.data?.message ?? axiosError.message}
      </p>
    )
  }

  if (!post) return null

  return (
    <div className="space-y-8 max-w-3xl mx-auto">
      <PostView post={post} />
      <CommentList postId={post.id} />
    </div>
  )
}
