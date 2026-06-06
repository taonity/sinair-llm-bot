import { NextResponse } from 'next/server'
import { getBuildInfo } from '@/lib/buildInfo'

export const dynamic = 'force-dynamic'

export async function GET() {
  return NextResponse.json(getBuildInfo())
}
