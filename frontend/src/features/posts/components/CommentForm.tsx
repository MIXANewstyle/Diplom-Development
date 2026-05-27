import { useState, type FormEvent } from 'react'

const MAX = 2000

export function CommentForm({
  initialValue = '',
  submitLabel = 'Отправить',
  placeholder = 'Напишите комментарий...',
  isPending,
  autoFocus = false,
  error,
  onSubmit,
  onCancel,
}: {
  initialValue?: string
  submitLabel?: string
  placeholder?: string
  isPending: boolean
  autoFocus?: boolean
  error?: string | null
  onSubmit: (content: string) => void
  onCancel?: () => void
}) {
  const [value, setValue] = useState(initialValue)
  const trimmed = value.trim()
  const isValid = trimmed.length >= 1 && trimmed.length <= MAX

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!isValid || isPending) return
    onSubmit(trimmed)
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-2">
      <textarea
        autoFocus={autoFocus}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        rows={3}
        maxLength={MAX}
        placeholder={placeholder}
        className="w-full border rounded px-3 py-2 text-sm focus:outline-none focus:ring focus:ring-blue-200"
      />
      <div className="flex items-center justify-between text-xs text-gray-500">
        <span>
          {value.length}/{MAX}
        </span>
        <div className="flex items-center gap-2">
          {error && <span className="text-red-500">{error}</span>}
          {onCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="text-gray-500 hover:text-gray-700 px-2 py-1"
            >
              Отмена
            </button>
          )}
          <button
            type="submit"
            disabled={!isValid || isPending}
            className="bg-blue-600 text-white text-sm px-3 py-1.5 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isPending ? 'Отправка...' : submitLabel}
          </button>
        </div>
      </div>
    </form>
  )
}
