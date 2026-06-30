'use client'

import { useEffect, useState } from 'react'
import { fetchAuthenticatedUserStatus, requestLogout } from '@/lib/auth'
import { getRuntimeConfig } from '@/lib/runtimeConfig'
import { getCookie } from '@/lib/cookies'
import ErrorNotification from '@/components/ErrorNotification'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import DataConsole from '@/features/console/DataConsole'
import { DevLoginSwitcher } from '@/features/console/DevLoginSwitcher'

interface HelloResponse {
  message: string
  email: string
}

export default function Home() {
  const [hello, setHello] = useState<HelloResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function checkAuth() {
      const result = await fetchAuthenticatedUserStatus()

      if (result.status === 'authenticated') {
        setHello(result.data)
      } else if (result.status === 'unauthenticated') {
        window.location.href = '/login'
        return
      } else {
        setError(result.message)
      }

      setLoading(false)
    }

    checkAuth()
  }, [])

  const handleLogout = async () => {
    try {
      const config = await getRuntimeConfig()
      const csrfCookieName = config.csrfCookieName || 'XSRF-TOKEN'
      const xsrfToken = getCookie(csrfCookieName) || ''

      await requestLogout(xsrfToken)
      window.location.href = '/login'
    } catch {
      setError('Logout failed. Please try again.')
    }
  }

  return (
    <div className="flex justify-center px-6 py-4">
      <div className="w-full max-w-[1200px]">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b pb-3">
          <div className="flex min-w-0 items-baseline gap-2">
            <span className="text-lg font-semibold">Data console</span>
            {loading ? (
              <Skeleton className="h-4 w-40" />
            ) : (
              hello && (
                <span className="truncate text-sm text-muted-foreground">{hello.email}</span>
              )
            )}
          </div>
          <Button variant="outline" size="sm" onClick={handleLogout}>
            Log out
          </Button>
        </header>

        <main className="pt-4">
          {error && <ErrorNotification message={error} onClose={() => setError(null)} />}
          {!loading && hello && <DataConsole />}
        </main>
      </div>
      {!loading && hello && <DevLoginSwitcher />}
    </div>
  )
}

