import type { NextRequest } from 'next/server'

export const LOCAL_BACKEND_URL = process.env.LOCAL_BACKEND_URL || ''
export const PUBLIC_BACKEND_URL = process.env.PUBLIC_BACKEND_URL || ''
const TIMEOUT = 60000

export async function fetchFromBackend(
  req: NextRequest,
  path: string,
  init: RequestInit = {},
) {
  const headers = new Headers(init.headers)
  const cookie = req.headers.get('cookie')
  if (cookie) {
    headers.set('cookie', cookie)
  }

  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), TIMEOUT)

  try {
    return await fetch(`${LOCAL_BACKEND_URL}${path}`, {
      ...init,
      headers,
      signal: controller.signal,
    })
  } catch (err) {
    if (err instanceof Error && err.name === 'AbortError') {
      return new Response(null, { status: 504 })
    }
    throw err
  } finally {
    clearTimeout(timeoutId)
  }
}
