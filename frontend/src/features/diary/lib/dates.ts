/**
 * Calendar helpers for the diary. All values are ISO dates (YYYY-MM-DD) in the browser's
 * local timezone: the server accepts the client's own date within a ±1 day window.
 */

const pad = (n: number) => String(n).padStart(2, '0')

export function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Parses YYYY-MM-DD as a local-midnight Date; returns null for anything else. */
export function parseIsoDate(iso: string | undefined | null): Date | null {
  if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) return null
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d) return null
  return date
}

export function todayIso(now: Date = new Date()): string {
  return toIsoDate(now)
}

export function addDays(iso: string, days: number): string {
  const d = parseIsoDate(iso)
  if (!d) throw new Error(`Invalid date: ${iso}`)
  d.setDate(d.getDate() + days)
  return toIsoDate(d)
}

export function monthKey(iso: string): string {
  return iso.slice(0, 7)
}

export function addMonths(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}

/** Monday of the ISO week containing the date. */
export function isoWeekStart(iso: string): string {
  const d = parseIsoDate(iso)
  if (!d) throw new Error(`Invalid date: ${iso}`)
  const dow = (d.getDay() + 6) % 7 // Monday = 0
  d.setDate(d.getDate() - dow)
  return toIsoDate(d)
}

export interface GridCell {
  iso: string
  inMonth: boolean
  day: number
}

/** 6×7 (or 5×7) grid of a month starting on Monday. */
export function buildMonthGrid(month: string): GridCell[][] {
  const [y, m] = month.split('-').map(Number)
  const first = new Date(y, m - 1, 1)
  const start = isoWeekStart(toIsoDate(first))
  const lastDay = new Date(y, m, 0).getDate()
  const rows: GridCell[][] = []
  let cursor = start
  while (true) {
    const row: GridCell[] = []
    for (let i = 0; i < 7; i++) {
      const d = parseIsoDate(cursor)!
      row.push({ iso: cursor, inMonth: d.getMonth() === m - 1, day: d.getDate() })
      cursor = addDays(cursor, 1)
    }
    rows.push(row)
    const next = parseIsoDate(cursor)!
    const passedMonth = next.getMonth() !== m - 1 || next.getDate() > lastDay
    if (passedMonth && rows.length >= 4) break
    if (rows.length >= 6) break
  }
  return rows
}

/** Client-side view of the writable window: today and yesterday (server validates ±1 day). */
export function isWritableLocally(iso: string, now: Date = new Date()): boolean {
  const today = todayIso(now)
  return iso === today || iso === addDays(today, -1)
}

export function isFutureLocally(iso: string, now: Date = new Date()): boolean {
  return iso > todayIso(now)
}

const longFormatter = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' })
const weekdayFormatter = new Intl.DateTimeFormat('ru-RU', { weekday: 'long' })
const monthFormatter = new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' })
const shortFormatter = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long' })

export function formatLong(iso: string): string {
  const d = parseIsoDate(iso)
  return d ? longFormatter.format(d) : iso
}

export function formatDayTitle(iso: string): string {
  const d = parseIsoDate(iso)
  return d ? `${longFormatter.format(d)}, ${weekdayFormatter.format(d)}` : iso
}

export function formatMonthTitle(month: string): string {
  const [y, m] = month.split('-').map(Number)
  const label = monthFormatter.format(new Date(y, m - 1, 1))
  return label.charAt(0).toUpperCase() + label.slice(1)
}

export function formatRange(startIso: string, endIso: string): string {
  const s = parseIsoDate(startIso)
  const e = parseIsoDate(endIso)
  if (!s || !e) return `${startIso} – ${endIso}`
  if (s.getFullYear() === e.getFullYear() && s.getMonth() === e.getMonth()) {
    return `${s.getDate()}–${longFormatter.format(e)}`
  }
  if (s.getFullYear() === e.getFullYear()) {
    return `${shortFormatter.format(s)} – ${longFormatter.format(e)}`
  }
  return `${longFormatter.format(s)} – ${longFormatter.format(e)}`
}
