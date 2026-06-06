import { NextRequest, NextResponse } from 'next/server'
import { fetchFromBackend } from '@/lib/backend'

export async function GET(req: NextRequest) {
  const res = await fetchFromBackend(req, '/user')
  return new NextResponse(res.body, { status: res.status, headers: res.headers })
}

export async function DELETE(req: NextRequest) {
  const xsrf = req.headers.get('x-xsrf-token') ?? ''
  const res = await fetchFromBackend(req, '/user', {
    method: 'DELETE',
    headers: { 'X-XSRF-TOKEN': xsrf },
  })
  return new NextResponse(res.body, { status: res.status, headers: res.headers })
}
