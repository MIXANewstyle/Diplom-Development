import type { ReactNode } from 'react'

interface Props {
  displayName: string
  children: ReactNode
}

export const GuestRoomLayout = ({ displayName, children }: Props) => {
  return (
    <div className="min-h-screen flex flex-col bg-white text-gray-700">
      <header className="border-b border-gray-200 shrink-0">
        <div className="max-w-4xl mx-auto px-4 py-3 flex items-center justify-between gap-3">
          <div className="font-bold text-lg text-blue-600">Диалог</div>
          <div className="text-sm text-gray-600 truncate">
            Гость: <span className="font-medium text-gray-900">{displayName}</span>
          </div>
        </div>
      </header>
      <main className="flex-1 w-full">{children}</main>
    </div>
  )
}
