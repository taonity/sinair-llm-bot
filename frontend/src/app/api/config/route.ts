import { NextResponse } from 'next/server'

export const dynamic = 'force-dynamic'
export const runtime = 'nodejs'

export async function GET() {
  return NextResponse.json({
    csrfCookieName: process.env.CSRF_COOKIE_NAME || '',
    publicBackendUrl: process.env.PUBLIC_BACKEND_URL || '',
  })
}
