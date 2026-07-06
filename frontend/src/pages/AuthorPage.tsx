import { useParams } from 'react-router-dom'
import { useAuthStore } from '../shared/stores/authStore'
import { resolveMediaUrl } from '../shared/lib/mediaUrl'
import { useAuthorPosts } from '../features/authors/hooks/useAuthorPosts'
import { useAuthorProfile } from '../features/authors/hooks/useAuthorProfile'
import { PostCard } from '../features/feed/components/PostCard'
import { FollowButton } from '../features/social/components/FollowButton'
import { getErrorMessage } from '../shared/lib/errors'
import type { AxiosError } from 'axios'

// ---------------------------------------------------------------------------
// Role badge helpers
// ---------------------------------------------------------------------------
const ROLE_LABELS: Record<string, string> = {
  AUTHOR: 'Автор',
  ADMIN: 'Администратор',
  BASIC: 'Подписчик',
  FREE: 'Пользователь',
  GUEST: 'Гость',
}

function RoleBadge({ role }: { role: string }) {
  const label = ROLE_LABELS[role] ?? role
  const isAuthor = role === 'AUTHOR'
  return (
    <span
      className={`inline-block px-2 py-0.5 rounded text-xs font-semibold ${
        isAuthor
          ? 'bg-blue-100 text-blue-700'
          : 'bg-gray-100 text-gray-600'
      }`}
    >
      {label}
    </span>
  )
}

// ---------------------------------------------------------------------------
// AuthorPage
// ---------------------------------------------------------------------------
export function AuthorPage() {
  const { authorId } = useParams<{ authorId: string }>()
  const user = useAuthStore((s) => s.user)

  const {
    data: profile,
    isPending: profilePending,
    error: profileError,
  } = useAuthorProfile(authorId || '')

  const {
    data,
    isPending: postsPending,
    error: postsError,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = useAuthorPosts({ authorId: authorId || '', tags: [] })

  const axiosPostsError = postsError as AxiosError<{ message?: string }> | null

  // Derived display values — authoritative from profile, never from first post
  const displayName = profile
    ? profile.fullName?.trim() || profile.username
    : null

  // ---------------------------------------------------------------------------
  // Profile header
  // ---------------------------------------------------------------------------
  const renderProfileHeader = () => {
    if (profilePending) {
      return (
        <div className="flex items-center gap-4 mb-6 pb-6 border-b border-gray-200">
          <div className="w-16 h-16 rounded-full bg-gray-200 animate-pulse shrink-0" />
          <div className="flex-1 space-y-2">
            <div className="h-6 bg-gray-200 rounded animate-pulse w-48" />
            <div className="h-4 bg-gray-100 rounded animate-pulse w-32" />
          </div>
        </div>
      )
    }

    if (profileError) {
      const status = (profileError as AxiosError)?.response?.status
      return (
        <div className="mb-6 pb-6 border-b border-gray-200">
          {status === 404 ? (
            <p className="text-gray-500 text-sm">Профиль автора недоступен</p>
          ) : (
            <p className="text-red-500 text-sm">{getErrorMessage(profileError)}</p>
          )}
        </div>
      )
    }

    if (!profile) return null

    const initials = displayName ? displayName.charAt(0).toUpperCase() : '?'

    return (
      <div className="mb-6 pb-6 border-b border-gray-200 space-y-4">
        {/* Top row: avatar + name block + follow button */}
        <div className="flex flex-col sm:flex-row sm:items-center gap-4 flex-wrap">
          {/* Avatar */}
          {profile.avatarUrl ? (
            <img
              src={resolveMediaUrl(profile.avatarUrl) || ''}
              alt={displayName || profile.username}
              className="w-16 h-16 rounded-full object-cover border border-gray-200 shrink-0"
            />
          ) : (
            <div className="w-16 h-16 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-bold text-xl shrink-0">
              {initials}
            </div>
          )}

          {/* Name + handle + role / follow */}
          <div className="flex-1 flex flex-col sm:flex-row sm:items-center justify-between gap-3 min-w-0 flex-wrap">
            <div className="min-w-0 space-y-0.5">
              <h1 className="text-2xl font-bold break-words leading-tight">{displayName}</h1>
              {profile.fullName?.trim() && profile.fullName !== profile.username && (
                <p className="text-sm text-gray-500 break-words">@{profile.username}</p>
              )}
              <RoleBadge role={profile.role} />
            </div>
            <FollowButton authorId={authorId || ''} />
          </div>
        </div>

        {/* Bio */}
        {profile.bio?.trim() && (
          <div>
            <p className="text-sm font-medium text-gray-500 mb-1">О себе</p>
            <p className="whitespace-pre-wrap break-words text-gray-800 text-sm leading-relaxed">
              {profile.bio.trim()}
            </p>
          </div>
        )}

        {/* Contact info */}
        {profile.contactInfo?.trim() && (
          <div>
            <p className="text-sm font-medium text-gray-500 mb-1">Контакты</p>
            <p className="whitespace-pre-wrap break-words text-gray-800 text-sm leading-relaxed">
              {profile.contactInfo.trim()}
            </p>
          </div>
        )}
      </div>
    )
  }

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------
  return (
    <div className="space-y-4 max-w-3xl">
      {renderProfileHeader()}

      {/* Posts loading */}
      {postsPending && <p className="text-gray-500">Загрузка публикаций...</p>}

      {/* Posts error */}
      {axiosPostsError && (
        <p className="text-red-500">
          {axiosPostsError.response?.status === 403
            ? `Для просмотра нужна подписка уровня BASIC или выше. Текущая роль: ${user?.role ?? 'неизвестна'}.`
            : axiosPostsError.response?.data?.message ?? axiosPostsError.message}
        </p>
      )}

      {/* Empty state */}
      {!postsError && data && data.pages.every((p) => p.items.length === 0) && (
        <p className="text-gray-500">Публикаций пока нет</p>
      )}

      {/* Post cards */}
      {!postsError &&
        data?.pages.map((page, pageIndex) =>
          page.items.map((post) => (
            <PostCard key={`${pageIndex}-${post.id}`} post={post} />
          )),
        )}

      {/* Pagination */}
      {!postsError && hasNextPage && (
        <button
          type="button"
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="w-full py-2 text-sm font-medium text-blue-600 border border-blue-600 rounded hover:bg-blue-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isFetchingNextPage ? 'Загрузка...' : 'Загрузить ещё'}
        </button>
      )}
    </div>
  )
}
