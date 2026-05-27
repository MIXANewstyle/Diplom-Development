import { FeedList } from '../features/feed/components/FeedList'

export function FeedPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Лента</h1>
      <FeedList />
    </div>
  )
}
