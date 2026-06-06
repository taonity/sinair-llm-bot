import { NextResponse } from 'next/server'

const BACKEND_URL = process.env.LOCAL_BACKEND_URL || ''

export async function GET() {
  const checks: Record<string, { status: string }> = {}

  try {
    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), 5000)
    const res = await fetch(`${BACKEND_URL}/actuator/health/liveness`, {
      signal: controller.signal,
    })
    clearTimeout(timeoutId)

    checks.backend = { status: res.ok ? 'UP' : 'DOWN' }
  } catch {
    checks.backend = { status: 'DOWN' }
  }

  const overallStatus = Object.values(checks).every((c) => c.status === 'UP')
    ? 'UP'
    : 'DOWN'

  return NextResponse.json(
    { status: overallStatus, checks },
    { status: overallStatus === 'UP' ? 200 : 503 },
  )
}
