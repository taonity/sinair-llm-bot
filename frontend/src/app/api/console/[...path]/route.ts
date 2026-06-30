import { type NextRequest, NextResponse } from 'next/server'
import { fetchFromBackend } from '@/lib/backend'

/**
 * Catch-all proxy for the data console. Forwards every /api/console/* request to the backend
 * /console/* endpoint, preserving the path, query string, method, body and CSRF token. Cookies are
 * forwarded by fetchFromBackend, so the backend sees the authenticated session.
 */
async function proxy(req: NextRequest, path: string[]) {
  const segments = path.map(encodeURIComponent).join('/')
  const search = req.nextUrl.search
  const backendPath = `/console/${segments}${search}`

  const headers: Record<string, string> = {}
  const xsrf = req.headers.get('x-xsrf-token')
  if (xsrf) {
    headers['X-XSRF-TOKEN'] = xsrf
  }

  const method = req.method.toUpperCase()
  const hasBody = method !== 'GET' && method !== 'HEAD' && method !== 'DELETE'
  let body: string | undefined
  if (hasBody) {
    body = await req.text()
    if (body) {
      const contentType = req.headers.get('content-type')
      headers['Content-Type'] = contentType ?? 'application/json'
    }
  }

  const res = await fetchFromBackend(req, backendPath, { method, headers, body })
  return new NextResponse(res.body, { status: res.status, headers: res.headers })
}

type Context = { params: Promise<{ path: string[] }> }

export async function GET(req: NextRequest, ctx: Context) {
  const { path } = await ctx.params
  return proxy(req, path)
}

export async function POST(req: NextRequest, ctx: Context) {
  const { path } = await ctx.params
  return proxy(req, path)
}

export async function PUT(req: NextRequest, ctx: Context) {
  const { path } = await ctx.params
  return proxy(req, path)
}

export async function DELETE(req: NextRequest, ctx: Context) {
  const { path } = await ctx.params
  return proxy(req, path)
}
