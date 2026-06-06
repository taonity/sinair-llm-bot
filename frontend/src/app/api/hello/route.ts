import { type NextRequest } from 'next/server'
import { fetchFromBackend } from '@/lib/backend'

export async function GET(req: NextRequest) {
  const response = await fetchFromBackend(req, '/hello')
  if (!response.ok) {
    return new Response(response.body, { status: response.status, headers: response.headers })
  }
  const data = await response.json()
  return Response.json(data, { status: response.status })
}
