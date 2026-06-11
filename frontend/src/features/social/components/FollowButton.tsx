import { useState } from 'react'
import { useAuthStore } from '../../../shared/stores/authStore'
import { useMyFollows, useFollow, useUnfollow } from '../hooks'
import { getErrorMessage } from '../../../shared/lib/errors'
import { ErrorText } from '../../../shared/components/ErrorText'

export function FollowButton({ authorId }: { authorId: string }) {
  const user = useAuthStore((s) => s.user)
  const { data: follows, isLoading } = useMyFollows(user?.id)
  const follow = useFollow()
  const unfollow = useUnfollow()
  const [errorMsg, setErrorMsg] = useState('')

  if (!user || user.id === authorId) return null

  const isFollowing = follows?.some((f) => f.authorId === authorId)
  const isPending = follow.isPending || unfollow.isPending || isLoading

  const handleToggle = async () => {
    setErrorMsg('')
    try {
      if (isFollowing) {
        await unfollow.mutateAsync(authorId)
      } else {
        await follow.mutateAsync(authorId)
      }
    } catch (err: unknown) {
      setErrorMsg(getErrorMessage(err))
    }
  }

  return (
    <div className="inline-flex flex-col gap-1 align-middle">
      {isFollowing ? (
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-green-700 bg-green-100 px-2 py-1 rounded">
            Вы подписаны
          </span>
          <button
            type="button"
            onClick={handleToggle}
            disabled={isPending}
            className="px-3 py-1 text-sm font-medium border border-gray-300 text-gray-700 rounded hover:bg-red-50 hover:text-red-700 hover:border-red-200 disabled:opacity-50 transition-colors"
          >
            Отписаться
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={handleToggle}
          disabled={isPending}
          className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
        >
          Подписаться
        </button>
      )}
      <ErrorText error={errorMsg} className="text-xs" />
    </div>
  )
}
