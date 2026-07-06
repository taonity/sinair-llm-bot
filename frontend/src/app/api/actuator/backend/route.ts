import { type NextRequest, NextResponse } from 'next/server'
import { fetchFromBackend } from '@/lib/backend'

export const dynamic = 'force-dynamic'

/**
 * Proxies the backend Spring Boot Actuator `/actuator/info` endpoint (app/git/build details).
 * The backend exposes it publicly, so this works on the login screen too.
 */
export async function GET(req: NextRequest) {
  const response = await fetchFromBackend(req, '/actuator/info')
  if (!response.ok) {
    return new NextResponse(null, { status: response.status })
  }
  const data = await response.json()
  return NextResponse.json(data, { status: 200 })
}
