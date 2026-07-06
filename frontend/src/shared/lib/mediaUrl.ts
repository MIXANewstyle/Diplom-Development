export function resolveMediaUrl(url: string | null | undefined): string | null {
  if (!url) return null
  if (url.startsWith('/')) {
    const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
    return baseUrl.replace(/\/+$/, '') + url
  }
  return url
}
