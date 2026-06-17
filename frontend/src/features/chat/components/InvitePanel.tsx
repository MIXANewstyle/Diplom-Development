import { useState } from 'react'
import { useMintInvite, useRevokeInvite } from '../hooks/useInvites'
import { buildInviteUrl } from '../invites'
import { getErrorMessage } from '../../../shared/lib/errors'

interface Props {
  roomId: string
}

export const InvitePanel = ({ roomId }: Props) => {
  const mintMutation = useMintInvite(roomId)
  const revokeMutation = useRevokeInvite(roomId)

  const [inviteUrl, setInviteUrl] = useState<string | null>(null)
  const [expiresAt, setExpiresAt] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [revoked, setRevoked] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleMint = async () => {
    setError(null)
    setCopied(false)
    setRevoked(false)
    try {
      const res = await mintMutation.mutateAsync()
      setInviteUrl(buildInviteUrl(res.token))
      setExpiresAt(res.expiresAt)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  const handleCopy = async () => {
    if (!inviteUrl) return
    try {
      await navigator.clipboard.writeText(inviteUrl)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      setError('Не удалось скопировать ссылку. Скопируйте вручную.')
    }
  }

  const handleRevoke = async () => {
    setError(null)
    try {
      await revokeMutation.mutateAsync()
      setInviteUrl(null)
      setExpiresAt(null)
      setCopied(false)
      setRevoked(true)
    } catch (e) {
      setError(getErrorMessage(e))
    }
  }

  const formattedExpiry = expiresAt
    ? new Date(expiresAt).toLocaleString('ru-RU', {
        day: 'numeric',
        month: 'long',
        hour: '2-digit',
        minute: '2-digit',
      })
    : null

  return (
    <div className="w-full max-w-md mx-auto space-y-4 text-left">
      <div className="text-center space-y-1">
        <p className="text-gray-700 font-medium">Пригласите собеседника по ссылке</p>
        <p className="text-sm text-gray-500">
          Создайте ссылку и отправьте её — собеседник присоединится к этой комнате.
        </p>
      </div>

      {!inviteUrl ? (
        <div className="flex flex-col items-center gap-2">
          <button
            onClick={handleMint}
            disabled={mintMutation.isPending}
            className="px-6 py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {mintMutation.isPending ? 'Создание...' : 'Создать ссылку'}
          </button>
          {revoked && (
            <p className="text-sm text-gray-500">Ссылка отозвана. Создайте новую при необходимости.</p>
          )}
        </div>
      ) : (
        <div className="space-y-3 bg-white border border-gray-200 rounded-lg p-4 shadow-sm">
          <div className="flex flex-col sm:flex-row gap-2">
            <input
              type="text"
              readOnly
              value={inviteUrl}
              onFocus={(e) => e.currentTarget.select()}
              className="flex-1 min-w-0 px-3 py-2 border border-gray-300 rounded text-sm bg-gray-50 text-gray-700"
            />
            <button
              onClick={handleCopy}
              className="shrink-0 px-4 py-2 bg-gray-100 text-gray-800 rounded font-medium text-sm hover:bg-gray-200"
            >
              {copied ? 'Скопировано' : 'Копировать'}
            </button>
          </div>

          {formattedExpiry && (
            <p className="text-xs text-gray-500">Ссылка действует до {formattedExpiry}</p>
          )}

          <div className="flex flex-wrap gap-2">
            <button
              onClick={handleMint}
              disabled={mintMutation.isPending}
              className="px-4 py-2 bg-blue-50 text-blue-700 rounded font-medium text-sm hover:bg-blue-100 disabled:opacity-50"
            >
              {mintMutation.isPending ? 'Создание...' : 'Создать новую'}
            </button>
            <button
              onClick={handleRevoke}
              disabled={revokeMutation.isPending}
              className="px-4 py-2 bg-gray-100 text-gray-800 rounded font-medium text-sm hover:bg-gray-200 disabled:opacity-50"
            >
              {revokeMutation.isPending ? 'Отзыв...' : 'Отозвать'}
            </button>
          </div>
          <p className="text-xs text-gray-400">
            При создании новой ссылки предыдущая перестаёт работать.
          </p>
        </div>
      )}

      {error && <p className="text-sm text-red-600 text-center">{error}</p>}
    </div>
  )
}
