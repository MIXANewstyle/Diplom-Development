import { useState } from 'react'

interface ComposerProps {
  isActive: boolean
  isPending: boolean
  onSubmit: (text: string) => void
}

export const Composer = ({ isActive, isPending, onSubmit }: ComposerProps) => {
  const [text, setText] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!text.trim() || !isActive || isPending) return
    onSubmit(text)
    setText('')
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit(e)
    }
  }

  if (!isActive) {
    return (
      <div className="p-4 bg-gray-50 border-t text-center text-gray-500">
        Сессия завершена. Отправка сообщений недоступна.
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="p-4 bg-white border-t flex flex-col">
      <textarea
        className="w-full border rounded-lg p-3 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
        rows={3}
        placeholder="Введите сообщение..."
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
        disabled={isPending}
        maxLength={8000}
      />
      <div className="mt-2 flex justify-between items-center">
        <span className="text-xs text-gray-400">
          {text.length} / 8000
        </span>
        <div className="flex items-center gap-4">
          {isPending && (
            <span className="text-sm text-gray-500 animate-pulse">
              ИИ печатает…
            </span>
          )}
          <button
            type="submit"
            disabled={!text.trim() || isPending}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Отправить
          </button>
        </div>
      </div>
    </form>
  )
}
