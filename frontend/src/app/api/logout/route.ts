import { NextRequest, NextResponse } from 'next/server'
import { fetchFromBackend } from '@/lib/backend'

export async function POST(req: NextRequest) {
  const xsrf = req.headers.get('x-xsrf-token') ?? ''
  const res = await fetchFromBackend(req, '/logout', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': xsrf },
  })
  return new NextResponse(res.body, { status: res.status, headers: res.headers })
}
