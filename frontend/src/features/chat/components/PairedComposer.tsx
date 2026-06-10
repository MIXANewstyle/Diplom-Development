import { useState, useEffect } from 'react'

interface Props {
  isActive: boolean
  archived: boolean
  hasFloor: boolean
  aiThinking: boolean
  onDraftUpsert: (bubbleId: string, text: string) => void
  onSubmit: () => void
}

export const PairedComposer = ({
  isActive,
  archived,
  hasFloor,
  aiThinking,
  onDraftUpsert,
  onSubmit,
}: Props) => {
  const [text, setText] = useState('')
  const [bubbleId, setBubbleId] = useState(() => crypto.randomUUID())
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Reset submitting state if AI finishes thinking
  useEffect(() => {
    if (!aiThinking) {
      setIsSubmitting(false)
    }
  }, [aiThinking])

  // Debounce draft upsert
  useEffect(() => {
    if (!hasFloor || !text.trim()) return
    const timer = setTimeout(() => {
      onDraftUpsert(bubbleId, text)
    }, 500)
    return () => clearTimeout(timer)
  }, [text, bubbleId, hasFloor, onDraftUpsert])

  const isDisabled = !isActive || archived || !hasFloor || aiThinking || isSubmitting

  let placeholder = 'Введите сообщение...'
  if (archived) placeholder = 'Диалог завершён'
  else if (!isActive) placeholder = 'Ожидание начала диалога...'
  else if (aiThinking) placeholder = 'Ожидание ответа ИИ...'
  else if (!hasFloor) placeholder = 'Ход собеседника...'

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!text.trim() || isDisabled) return

    setIsSubmitting(true)
    onDraftUpsert(bubbleId, text)
    onSubmit()
    
    // Clear for next turn
    setText('')
    setBubbleId(crypto.randomUUID())
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-3">
      <form onSubmit={handleSubmit} className="flex flex-col gap-2">
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          disabled={isDisabled}
          maxLength={8000}
          className="w-full h-24 p-2 text-sm border-0 focus:ring-0 resize-none outline-none disabled:bg-gray-50 disabled:text-gray-500 bg-transparent font-mono"
          placeholder={placeholder}
        />
        <div className="flex justify-between items-center">
          <div className="text-xs text-gray-400">
            {text.length} / 8000
          </div>
          <button
            type="submit"
            disabled={isDisabled || !text.trim()}
            className="px-6 py-2 bg-blue-600 text-white rounded font-medium text-sm hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            {isSubmitting ? 'Отправка...' : 'Отправить'}
          </button>
        </div>
      </form>
    </div>
  )
}
