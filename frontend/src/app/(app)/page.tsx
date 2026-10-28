'use client'

import { useEffect, useState } from 'react'
import {
  fetchAuthenticatedUserStatus,
  POST_LOGIN_PATH_KEY,
  requestLogout,
  safeLocalPath,
} from '@/lib/auth'
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
        const returnPath = safeLocalPath(sessionStorage.getItem(POST_LOGIN_PATH_KEY))
        sessionStorage.removeItem(POST_LOGIN_PATH_KEY)
        const currentPath = `${location.pathname}${location.search}${location.hash}`
        if (returnPath && returnPath !== currentPath) {
          location.replace(returnPath)
          return
        }
        setHello(result.data)
      } else if (result.status === 'unauthenticated') {
        const returnPath = `${location.pathname}${location.search}${location.hash}`
        window.location.href = `/login?next=${encodeURIComponent(returnPath)}`
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
    <div className="console-shell px-2 py-2 sm:px-6 sm:py-4">
      <div className="mx-auto w-full min-w-0 max-w-[1200px]">
        <header className="console-header flex items-center justify-between gap-2 border-b pb-2 pr-12 sm:pb-3 min-[1300px]:pr-0">
          <div className="flex min-w-0 flex-col sm:flex-row sm:items-baseline sm:gap-2">
            <span className="text-base font-semibold sm:text-lg">Data console</span>
            {loading ? (
              <Skeleton className="h-4 w-40" />
            ) : (
              hello && (
                <span className="truncate text-xs text-muted-foreground sm:text-sm" title={hello.email}>{hello.email}</span>
              )
            )}
          </div>
          <Button variant="outline" size="sm" onClick={handleLogout}>
            Log out
          </Button>
        </header>

        <main className="min-w-0 pt-2 sm:pt-4">
          {error && <ErrorNotification message={error} onClose={() => setError(null)} />}
          {!loading && hello && <DataConsole />}
        </main>
      </div>
      {!loading && hello && <DevLoginSwitcher />}
    </div>
  )
}

