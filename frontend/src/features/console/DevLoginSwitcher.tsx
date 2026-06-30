'use client'

import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { getRuntimeConfig } from '@/lib/runtimeConfig'
import type { StubLogin } from './types'

/**
 * Dev-only "log in as" switcher. Fetches the stub login shortcuts exposed by the backend
 * (only present under the local `stub-google` profile) and links each to the real OAuth2 flow,
 * so request/approve can be tested end-to-end as different users. Renders nothing when the
 * endpoint is unavailable (e.g. production or real Google).
 */
export function DevLoginSwitcher() {
  const [logins, setLogins] = useState<StubLogin[] | null>(null)
  const [backendUrl, setBackendUrl] = useState('')

  useEffect(() => {
    let active = true
    void (async () => {
      try {
        const res = await fetch('/api/dev/stub-users', { cache: 'no-store' })
        if (!res.ok) return
        const data = (await res.json()) as StubLogin[]
        const config = await getRuntimeConfig()
        if (!active) return
        setBackendUrl(config.publicBackendUrl || '')
        setLogins(data)
      } catch {
        // Endpoint absent → not in stub mode; keep the switcher hidden.
      }
    })()
    return () => {
      active = false
    }
  }, [])

  if (!logins || logins.length === 0) return null

  return (
    <div className="fixed right-3 bottom-3 z-50 flex flex-col gap-1.5 rounded-lg border bg-card/95 p-2 shadow-lg backdrop-blur">
      <span className="px-0.5 text-[10px] font-semibold tracking-wide text-muted-foreground uppercase">
        Log in as · dev
      </span>
      <div className="flex flex-wrap gap-1">
        {logins.map((l) => (
          <Button
            key={l.registrationId}
            size="xs"
            variant="outline"
            onClick={() => {
              window.location.href = `${backendUrl}/oauth2/authorization/${l.registrationId}`
            }}
          >
            {l.label}
          </Button>
        ))}
      </div>
    </div>
  )
}
