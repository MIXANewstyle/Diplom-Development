export function formatDate(iso: string): string {
  const date = parseDate(iso)
  if (!date) return '—'
  return date.toLocaleDateString('ru-RU')
}

export function formatDateTime(iso: string | null | undefined): string {
  const date = parseDate(iso)
  if (!date) return '—'
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function parseDate(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}
