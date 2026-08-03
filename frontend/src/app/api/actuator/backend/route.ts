import { type NextRequest, NextResponse } from 'next/server'
import { fetchFromBackend } from '@/lib/backend'

export const dynamic = 'force-dynamic'

export async function GET(req: NextRequest) {
  const response = await fetchFromBackend(req, '/actuator/info')
  if (!response.ok) {
    return new NextResponse(null, { status: response.status })
  }
  const data = await response.json()
  return NextResponse.json(data, { status: 200 })
}
