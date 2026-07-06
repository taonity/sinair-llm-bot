import { fetchWithTimeout } from '@/lib/clientApi'

/**
 * Actuator-style info payloads are open-ended maps (app/git/build/... vary between the
 * backend Spring Boot Actuator and the frontend build metadata). We keep them as generic
 * nested records and flatten them for display so every provided field is shown.
 */
export type InfoValue = string | number | boolean | null | InfoObject | InfoValue[]
export interface InfoObject {
  [key: string]: InfoValue
}

export interface AppInfoSource {
  /** Human label shown as the section title, e.g. "Backend" / "Frontend". */
  label: string
  /** The raw info payload, or null when it could not be loaded. */
  data: InfoObject | null
}

/** A single flattened key/value row, e.g. { key: "git.commit.id.abbrev", value: "18a81dc" }. */
export interface InfoRow {
  key: string
  value: string
}

/** Base URL of the GitHub repository, used to linkify commit SHAs. */
export const GITHUB_REPO_URL = (
  process.env.NEXT_PUBLIC_GITHUB_REPO_URL || 'https://github.com/taonity/sinair-llm-bot'
).replace(/\.git$/, '').replace(/\/$/, '')

/**
 * Returns a GitHub commit URL when the row looks like a commit SHA (a `commit` key whose
 * value is a 7–40 char hex string), otherwise null. Describe values like "18a81dc-dirty"
 * are intentionally excluded since they are not addressable commits.
 */
export function commitUrl(row: InfoRow): string | null {
  if (!/commit/i.test(row.key)) {
    return null
  }
  if (!/^[0-9a-f]{7,40}$/i.test(row.value)) {
    return null
  }
  return `${GITHUB_REPO_URL}/commit/${row.value}`
}

/** Fetches the frontend's own build/runtime info. */
export async function fetchFrontendInfo(): Promise<InfoObject | null> {
  return fetchInfo('/api/actuator/info')
}

/** Fetches the backend Spring Boot Actuator info (proxied through Next.js). */
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

/**
 * Flattens a nested info object into dotted-key rows so that every provided field is
 * rendered regardless of the payload's shape.
 */
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
