import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <main className="p-8">
      <h1 className="text-2xl font-bold">NotFoundPage</h1>
      <nav className="mt-4">
        <Link to="/" className="text-blue-600 underline">
          Home
        </Link>
      </nav>
    </main>
  )
}
