import { useState } from 'react'
import { useAuthStore } from '../shared/stores/authStore'
import { 
  useFriends, 
  useSendFriendRequest, 
  useAcceptFriendRequest, 
  useDeclineFriendRequest, 
  useCancelFriendRequest,
  useUserSearch,
  useMyFollows,
  useUsersBatch,
  useUnfollow
} from '../features/social/hooks'
import axios from 'axios'
import type { UserBrief } from '../features/social/types'

// Generic error handler
function handleReqErr(err: unknown, setter: (msg: string) => void) {
  if (axios.isAxiosError(err)) {
    setter(err.response?.data?.message || 'Ошибка выполнения действия')
  } else {
    setter('Неизвестная ошибка')
  }
}

function UserRow({ 
  user, 
  actions,
  errorMsg
}: { 
  user: UserBrief, 
  actions: React.ReactNode,
  errorMsg?: string
}) {
  return (
    <div className="flex flex-col py-3 border-b last:border-0 border-gray-100">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          {user.avatarUrl ? (
            <img src={user.avatarUrl} alt={user.username} className="w-10 h-10 rounded-full object-cover" />
          ) : (
            <div className="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-bold">
              {user.username.charAt(0).toUpperCase()}
            </div>
          )}
          <span className="font-medium text-gray-900">{user.username}</span>
        </div>
        <div className="flex gap-2">
          {actions}
        </div>
      </div>
      {errorMsg && <div className="text-red-500 text-xs mt-1 text-right">{errorMsg}</div>}
    </div>
  )
}

function FollowsSection() {
  const me = useAuthStore(s => s.user)
  const { data: follows, isLoading: followsLoading } = useMyFollows(me?.id)
  const { data: followedUsers, isLoading: usersLoading } = useUsersBatch(follows?.map(f => f.authorId) || [])
  const { mutateAsync: unfollow, isPending: isUnfollowing } = useUnfollow()
  const [errorMsg, setErrorMsg] = useState('')

  if (followsLoading || usersLoading) return <div className="p-4 text-gray-500">Загрузка...</div>
  if (!followedUsers || followedUsers.length === 0) return <div className="p-4 text-gray-500">Вы ни на кого не подписаны.</div>

  const handleUnfollow = async (authorId: string) => {
    setErrorMsg('')
    try {
      await unfollow(authorId)
    } catch (err) {
      handleReqErr(err, setErrorMsg)
    }
  }

  return (
    <div className="px-4 pb-2">
      {errorMsg && <div className="text-red-500 text-sm mb-2">{errorMsg}</div>}
      {followedUsers.map(u => (
        <UserRow 
          key={u.id} 
          user={u} 
          actions={
            <button 
              onClick={() => handleUnfollow(u.id)}
              disabled={isUnfollowing}
              className="text-sm px-3 py-1 bg-gray-100 text-gray-700 hover:bg-red-50 hover:text-red-600 rounded disabled:opacity-50"
            >
              Отписаться
            </button>
          } 
        />
      ))}
    </div>
  )
}

function SearchSection() {
  const [inputValue, setInputValue] = useState('')
  const [query, setQuery] = useState('')
  const [reqErrors, setReqErrors] = useState<Record<string, string>>({})
  
  const { data: results, isPending } = useUserSearch(query)
  const { mutateAsync: sendReq, isPending: isSending } = useSendFriendRequest()

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setQuery(inputValue)
  }

  const handleAdd = async (id: string) => {
    setReqErrors(prev => ({ ...prev, [id]: '' }))
    try {
      await sendReq(id)
    } catch (err) {
      handleReqErr(err, msg => setReqErrors(prev => ({ ...prev, [id]: msg })))
    }
  }

  return (
    <div className="p-4">
      <form onSubmit={handleSearch} className="flex gap-2 mb-4">
        <input 
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="Имя пользователя..."
          className="flex-1 border border-gray-300 rounded px-3 py-2 text-sm"
        />
        <button 
          type="submit"
          disabled={inputValue.trim().length < 2}
          className="px-4 py-2 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
        >
          Искать
        </button>
      </form>

      {query.length >= 2 && isPending && <p className="text-gray-500 text-sm">Поиск...</p>}
      
      {results && results.length === 0 && query.length >= 2 && (
        <p className="text-gray-500 text-sm">Ничего не найдено</p>
      )}

      {results?.map(u => (
        <UserRow 
          key={u.id} 
          user={u} 
          errorMsg={reqErrors[u.id]}
          actions={
            <button 
              onClick={() => handleAdd(u.id)}
              disabled={isSending}
              className="text-sm px-3 py-1 bg-green-50 text-green-700 hover:bg-green-100 rounded disabled:opacity-50"
            >
              Добавить
            </button>
          } 
        />
      ))}
    </div>
  )
}

export function FriendsPage() {
  const { data: friendsData, isLoading: friendsLoading } = useFriends()
  const { mutateAsync: acceptReq, isPending: isAccepting } = useAcceptFriendRequest()
  const { mutateAsync: declineReq, isPending: isDeclining } = useDeclineFriendRequest()
  const { mutateAsync: cancelReq, isPending: isCanceling } = useCancelFriendRequest()
  const [actionErrors, setActionErrors] = useState<Record<string, string>>({})

  const doAction = async (id: string, action: () => Promise<void>) => {
    setActionErrors(prev => ({ ...prev, [id]: '' }))
    try {
      await action()
    } catch (err) {
      handleReqErr(err, msg => setActionErrors(prev => ({ ...prev, [id]: msg })))
    }
  }

  const isBusy = isAccepting || isDeclining || isCanceling

  return (
    <div className="space-y-6 max-w-2xl mx-auto">
      <h1 className="text-2xl font-bold">Друзья и Подписки</h1>

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <h2 className="bg-gray-50 px-4 py-3 border-b border-gray-200 font-bold text-gray-800">Поиск людей</h2>
        <SearchSection />
      </div>

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <h2 className="bg-gray-50 px-4 py-3 border-b border-gray-200 font-bold text-gray-800">
          Входящие заявки
          {friendsData && friendsData.incomingRequests.length > 0 && (
            <span className="ml-2 bg-red-100 text-red-700 text-xs px-2 py-0.5 rounded-full">{friendsData.incomingRequests.length}</span>
          )}
        </h2>
        <div className="px-4 pb-2">
          {friendsLoading ? <p className="text-gray-500 py-2">Загрузка...</p> : 
           friendsData?.incomingRequests.length === 0 ? <p className="text-gray-500 py-2">Нет входящих заявок</p> :
           friendsData?.incomingRequests.map(u => (
             <UserRow 
               key={u.id} user={u} errorMsg={actionErrors[u.id]}
               actions={
                 <>
                   <button 
                     onClick={() => doAction(u.id, () => acceptReq(u.id))}
                     disabled={isBusy}
                     className="text-sm px-3 py-1 bg-green-600 text-white hover:bg-green-700 rounded disabled:opacity-50"
                   >
                     Принять
                   </button>
                   <button 
                     onClick={() => doAction(u.id, () => declineReq(u.id))}
                     disabled={isBusy}
                     className="text-sm px-3 py-1 bg-gray-100 text-gray-700 hover:bg-gray-200 rounded disabled:opacity-50"
                   >
                     Отклонить
                   </button>
                 </>
               }
             />
           ))}
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <h2 className="bg-gray-50 px-4 py-3 border-b border-gray-200 font-bold text-gray-800">
          Исходящие заявки
        </h2>
        <div className="px-4 pb-2">
          {friendsLoading ? <p className="text-gray-500 py-2">Загрузка...</p> : 
           friendsData?.outgoingRequests.length === 0 ? <p className="text-gray-500 py-2">Нет исходящих заявок</p> :
           friendsData?.outgoingRequests.map(u => (
             <UserRow 
               key={u.id} user={u} errorMsg={actionErrors[u.id]}
               actions={
                 <button 
                   onClick={() => doAction(u.id, () => cancelReq(u.id))}
                   disabled={isBusy}
                   className="text-sm px-3 py-1 bg-gray-100 text-gray-700 hover:bg-gray-200 rounded disabled:opacity-50"
                 >
                   Отменить
                 </button>
               }
             />
           ))}
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <h2 className="bg-gray-50 px-4 py-3 border-b border-gray-200 font-bold text-gray-800">
          Мои друзья
          {friendsData && friendsData.friends.length > 0 && (
            <span className="ml-2 bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded-full">{friendsData.friends.length}</span>
          )}
        </h2>
        <div className="px-4 pb-2">
          {friendsLoading ? <p className="text-gray-500 py-2">Загрузка...</p> : 
           friendsData?.friends.length === 0 ? <p className="text-gray-500 py-2">У вас пока нет друзей</p> :
           friendsData?.friends.map(u => (
             <UserRow key={u.id} user={u} actions={null} />
           ))}
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <h2 className="bg-gray-50 px-4 py-3 border-b border-gray-200 font-bold text-gray-800">
          Мои подписки
        </h2>
        <FollowsSection />
      </div>

    </div>
  )
}
