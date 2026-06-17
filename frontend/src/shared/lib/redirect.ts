// Reads a `redirect` query param and returns it only when it is a safe
// internal path (prevents open-redirect to external origins).
export function getSafeRedirect(search: string, fallback = '/'): string {
  const redirect = new URLSearchParams(search).get('redirect')
  if (redirect && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return fallback
}
