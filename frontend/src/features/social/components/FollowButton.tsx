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
    <div className="flex flex-col gap-1">
      <button
        type="button"
        onClick={handleToggle}
        disabled={isPending}
        className={`px-4 py-1.5 rounded text-sm font-medium transition-colors disabled:opacity-50
          ${
            isFollowing
              ? 'bg-gray-200 text-gray-800 hover:bg-red-100 hover:text-red-700 content-["Отписаться"]'
              : 'bg-blue-600 text-white hover:bg-blue-700'
          }`}
      >
        {isFollowing ? 'Отписаться' : 'Подписаться'}
      </button>
      <ErrorText error={errorMsg} className="text-xs" />
    </div>
  )
}
