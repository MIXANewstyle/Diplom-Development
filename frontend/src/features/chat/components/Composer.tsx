import { useState } from 'react'

interface ComposerProps {
  isActive: boolean
  isPending: boolean
  onSubmit: (text: string, restoreText: () => void) => void
}

export const Composer = ({ isActive, isPending, onSubmit }: ComposerProps) => {
  const [text, setText] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!text.trim() || !isActive || isPending) return
    const currentText = text
    setText('')
    onSubmit(currentText, () => setText(currentText))
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
    <form onSubmit={handleSubmit} className="p-3 md:p-4 bg-white border-t flex flex-col gap-2">
      <textarea
        className="w-full border rounded-lg p-3 resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-500"
        rows={3}
        placeholder="Введите сообщение..."
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
        disabled={isPending}
        maxLength={2000}
      />
      <div className="flex justify-between items-center gap-2 flex-wrap">
        <div className="flex items-center gap-3">
          <span className="text-xs text-gray-400">{text.length} / 2000</span>
        </div>
        <button
          type="submit"
          disabled={!text.trim() || isPending}
          className="w-full sm:w-auto px-6 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          Отправить
        </button>
      </div>
    </form>
  )
}
