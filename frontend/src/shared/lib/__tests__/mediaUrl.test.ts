import { describe, it, expect } from 'vitest'
import { resolveMediaUrl } from '../mediaUrl'

describe('resolveMediaUrl', () => {
  it('returns null for null/empty', () => {
    expect(resolveMediaUrl(null)).toBeNull()
    expect(resolveMediaUrl(undefined)).toBeNull()
    expect(resolveMediaUrl('')).toBeNull()
  })

  it('leaves absolute URLs unchanged', () => {
    expect(resolveMediaUrl('http://example.com/img.png')).toBe('http://example.com/img.png')
    expect(resolveMediaUrl('https://example.com/img.png')).toBe('https://example.com/img.png')
  })

  it('leaves non-slash relative paths unchanged', () => {
    expect(resolveMediaUrl('img.png')).toBe('img.png')
  })

  it('prefixes paths starting with / with base URL', () => {
    // import.meta.env is empty in unit tests by default, so it falls back to localhost:8080
    expect(resolveMediaUrl('/api/v1/uploads/files/test.png')).toBe('http://localhost:8080/api/v1/uploads/files/test.png')
  })
})
