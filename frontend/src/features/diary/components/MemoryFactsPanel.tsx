import { useState } from 'react'
import { Pencil, Trash2, Check, X } from 'lucide-react'
import type { MemoryFactCategory, MemoryFactResponse } from '../types'
import { useDeleteMemoryFact, useMemoryFacts, useUpdateMemoryFact } from '../hooks/useDiary'
import { getErrorMessage } from '../../../shared/lib/errors'

const CATEGORY_LABELS: Record<MemoryFactCategory, string> = {
  PERSON: 'Люди',
  THEME: 'Повторяющиеся темы',
  GOAL: 'Цели',
  TRIGGER: 'Триггеры',
  VALUE: 'Ценности',
  OTHER: 'Другое',
}

const ORDER: MemoryFactCategory[] = ['PERSON', 'THEME', 'GOAL', 'TRIGGER', 'VALUE', 'OTHER']

export const MemoryFactsPanel = () => {
  const { data: facts, isLoading } = useMemoryFacts()
  const updateMutation = useUpdateMemoryFact()
  const deleteMutation = useDeleteMemoryFact()
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')

  const startEdit = (f: MemoryFactResponse) => {
    setEditingId(f.id)
    setDraft(f.content)
  }
  const submitEdit = () => {
    if (!editingId || !draft.trim()) return
    updateMutation.mutate(
      { id: editingId, content: draft.trim() },
      {
        onSuccess: () => setEditingId(null),
        onError: (e) => alert(getErrorMessage(e)),
      },
    )
  }
  const remove = (f: MemoryFactResponse) => {
    if (!window.confirm('Забыть этот факт? Он исчезнет из памяти собеседника.')) return
    deleteMutation.mutate(f.id, { onError: (e) => alert(getErrorMessage(e)) })
  }

  const grouped = ORDER.map((cat) => ({
    cat,
    items: (facts ?? []).filter((f) => f.category === cat),
  })).filter((g) => g.items.length > 0)

  return (
    <div className="bg-white rounded-lg shadow p-4">
      <h3 className="font-semibold text-gray-900">Что собеседник помнит о вас</h3>
      <p className="text-xs text-gray-500 mb-3">
        Устойчивые факты из ваших записей. Они всегда в контексте разговора; любой можно поправить или забыть.
      </p>
      {isLoading && <div className="text-sm text-gray-500">Загрузка…</div>}
      {!isLoading && grouped.length === 0 && (
        <div className="text-sm text-gray-500">Пока пусто — факты появляются после подведения итогов дня.</div>
      )}
      <div className="space-y-3">
        {grouped.map((g) => (
          <div key={g.cat}>
            <div className="text-xs font-medium uppercase tracking-wide text-gray-400 mb-1">{CATEGORY_LABELS[g.cat]}</div>
            <ul className="space-y-1">
              {g.items.map((f) => (
                <li key={f.id} className="group flex items-start gap-2 text-sm text-gray-800">
                  {editingId === f.id ? (
                    <>
                      <input
                        value={draft}
                        onChange={(e) => setDraft(e.target.value)}
                        maxLength={300}
                        autoFocus
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') submitEdit()
                          if (e.key === 'Escape') setEditingId(null)
                        }}
                        className="flex-1 border-b border-blue-500 outline-none bg-transparent"
                      />
                      <button onClick={submitEdit} className="p-0.5 text-green-600" title="Сохранить"><Check size={16} /></button>
                      <button onClick={() => setEditingId(null)} className="p-0.5 text-gray-400" title="Отмена"><X size={16} /></button>
                    </>
                  ) : (
                    <>
                      <span className="flex-1">{f.content}</span>
                      <button onClick={() => startEdit(f)} className="p-0.5 text-gray-300 hover:text-blue-600 opacity-0 group-hover:opacity-100 focus:opacity-100" title="Поправить"><Pencil size={14} /></button>
                      <button onClick={() => remove(f)} className="p-0.5 text-gray-300 hover:text-red-600 opacity-0 group-hover:opacity-100 focus:opacity-100" title="Забыть"><Trash2 size={14} /></button>
                    </>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </div>
  )
}
