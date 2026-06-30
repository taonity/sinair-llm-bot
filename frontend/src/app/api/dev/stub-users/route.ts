import { type NextRequest, NextResponse } from 'next/server'
import { fetchFromBackend } from '@/lib/backend'

/**
 * Dev-only proxy: forwards to the backend `/dev/stub-users`, which only exists under the local
 * `stub-google` profile. Returns 404 in production, which the frontend treats as "hide the switcher".
 */
export async function GET(req: NextRequest) {
  const res = await fetchFromBackend(req, '/dev/stub-users')
  return new NextResponse(res.body, { status: res.status, headers: res.headers })
}
