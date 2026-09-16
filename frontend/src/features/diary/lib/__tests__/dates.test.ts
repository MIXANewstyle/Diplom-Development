import { describe, it, expect } from 'vitest'
import {
  addDays,
  addMonths,
  buildMonthGrid,
  formatRange,
  isWritableLocally,
  isoWeekStart,
  parseIsoDate,
  toIsoDate,
} from '../dates'

describe('diary dates', () => {
  it('round-trips local ISO dates', () => {
    expect(toIsoDate(new Date(2026, 8, 16))).toBe('2026-09-16')
    expect(parseIsoDate('2026-09-16')?.getDate()).toBe(16)
    expect(parseIsoDate('2026-02-30')).toBeNull()
    expect(parseIsoDate('16.09.2026')).toBeNull()
  })

  it('adds days across month and year boundaries', () => {
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31')
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29')
    expect(addMonths('2026-12', 1)).toBe('2027-01')
    expect(addMonths('2026-01', -1)).toBe('2025-12')
  })

  it('finds the ISO week start (Monday) across the year boundary', () => {
    expect(isoWeekStart('2026-01-01')).toBe('2025-12-29') // Thursday → previous Monday
    expect(isoWeekStart('2025-12-29')).toBe('2025-12-29')
    expect(isoWeekStart('2026-09-20')).toBe('2026-09-14') // Sunday → its Monday
  })

  it('builds a Monday-first month grid covering the whole month', () => {
    const grid = buildMonthGrid('2026-09') // 1 Sep 2026 is a Tuesday
    expect(grid[0][0].iso).toBe('2026-08-31')
    expect(grid[0][0].inMonth).toBe(false)
    expect(grid[0][1].iso).toBe('2026-09-01')
    expect(grid.every((row) => row.length === 7)).toBe(true)
    const all = grid.flat().map((c) => c.iso)
    expect(all).toContain('2026-09-30')
    expect(grid.length).toBeGreaterThanOrEqual(5)
    expect(grid.length).toBeLessThanOrEqual(6)
  })

  it('treats only today and yesterday as writable', () => {
    const now = new Date(2026, 8, 16, 23, 30)
    expect(isWritableLocally('2026-09-16', now)).toBe(true)
    expect(isWritableLocally('2026-09-15', now)).toBe(true)
    expect(isWritableLocally('2026-09-14', now)).toBe(false)
    expect(isWritableLocally('2026-09-17', now)).toBe(false)
  })

  it('formats ranges in Russian', () => {
    expect(formatRange('2026-09-07', '2026-09-13')).toBe('7–13 сентября 2026 г.')
    expect(formatRange('2025-12-29', '2026-01-04')).toBe('29 декабря 2025 г. – 4 января 2026 г.')
  })
})
