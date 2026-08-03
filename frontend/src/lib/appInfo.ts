import { fetchWithTimeout } from '@/lib/clientApi'

export type InfoValue = string | number | boolean | null | InfoObject | InfoValue[]
export interface InfoObject {
  [key: string]: InfoValue
}

export interface AppInfoSource {
  label: string
  data: InfoObject | null
}

export interface InfoRow {
  key: string
  value: string
}

export const GITHUB_REPO_URL = (
  process.env.NEXT_PUBLIC_GITHUB_REPO_URL || 'https://github.com/taonity/sinair-llm-bot'
).replace(/\.git$/, '').replace(/\/$/, '')

export function commitUrl(row: InfoRow): string | null {
  if (!/commit/i.test(row.key)) {
    return null
  }
  if (!/^[0-9a-f]{7,40}$/i.test(row.value)) {
    return null
  }
  return `${GITHUB_REPO_URL}/commit/${row.value}`
}

export async function fetchFrontendInfo(): Promise<InfoObject | null> {
  return fetchInfo('/api/actuator/info')
}

export async function fetchBackendInfo(): Promise<InfoObject | null> {
  return fetchInfo('/api/actuator/backend')
}

async function fetchInfo(url: string): Promise<InfoObject | null> {
  try {
    const res = await fetchWithTimeout(url, { timeoutMs: 8000 })
    if (!res.ok) {
      return null
    }
    return (await res.json()) as InfoObject
  } catch {
    return null
  }
}

export function flattenInfo(data: InfoObject | null): InfoRow[] {
  if (!data) {
    return []
  }
  const rows: InfoRow[] = []

  const walk = (value: InfoValue, path: string) => {
    if (value === null || value === undefined) {
      rows.push({ key: path, value: '—' })
      return
    }
    if (Array.isArray(value)) {
      value.forEach((item, index) => walk(item, `${path}[${index}]`))
      return
    }
    if (typeof value === 'object') {
      const entries = Object.entries(value)
      if (entries.length === 0) {
        rows.push({ key: path, value: '—' })
        return
      }
      for (const [childKey, childValue] of entries) {
        walk(childValue, path ? `${path}.${childKey}` : childKey)
      }
      return
    }
    rows.push({ key: path, value: String(value) })
  }

  walk(data, '')
  return rows
}
