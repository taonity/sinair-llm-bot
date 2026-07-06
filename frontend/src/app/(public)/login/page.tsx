'use client'

import { useEffect, useState } from 'react'
import { checkBackendLiveness } from '@/lib/auth'
import { getRuntimeConfig } from '@/lib/runtimeConfig'
import { getCookie, deleteCookie } from '@/lib/cookies'
import ErrorNotification from '@/components/ErrorNotification'
import { DevLoginSwitcher } from '@/features/console/DevLoginSwitcher'
import { AppInfoPanel } from '@/features/info/AppInfoPanel'
import { ChevronDown } from 'lucide-react'

const AUTH_ERROR_MESSAGES: Record<string, string> = {
  UNAUTHORIZED_ACCOUNT: 'Your account is not authorized to access this application.',
  AUTHENTICATION_FAILED: 'Authentication failed. Please try again.',
}

export default function LoginPage() {
  const [error, setError] = useState<string | null>(null)
  const [isChecking, setIsChecking] = useState(false)

  useEffect(() => {
    const handlePageShow = (event: PageTransitionEvent) => {
      if (event.persisted) {
        setIsChecking(false)
      }
    }
    window.addEventListener('pageshow', handlePageShow)
    return () => window.removeEventListener('pageshow', handlePageShow)
  }, [])

  useEffect(() => {
    const authError = getCookie('auth_error')
    if (authError) {
      deleteCookie('auth_error')
      setError(AUTH_ERROR_MESSAGES[authError] ?? 'Authentication failed. Please try again.')
    }
  }, [])

  useEffect(() => {
    fetch('/api/hello')
      .then((res) => {
        if (res.ok) {
          window.location.href = '/'
        }
      })
      .catch(() => {
        // Ignore — backend may be down, login page still renders
      })
  }, [])

  const handleLogin = async () => {
    setError(null)
    setIsChecking(true)

    const liveness = await checkBackendLiveness()

    if (!liveness.ok) {
      setError(liveness.message)
      setIsChecking(false)
      return
    }

    const config = await getRuntimeConfig()
    const loginUrl = config.publicBackendUrl
      ? `${config.publicBackendUrl}/oauth2/authorization/google-sinair-llm-bot`
      : '/oauth2/authorization/google-sinair-llm-bot'

    window.location.href = loginUrl
  }

  return (
    <div>
      <section className="relative flex min-h-screen flex-col items-center justify-center px-8">
        <div className="login-card">
          <h1 className="login-title">Sinair LLM Bot</h1>
          <p className="login-subtitle">
            Sign in to access your application
          </p>

          {error && (
            <ErrorNotification message={error} onClose={() => setError(null)} />
          )}

          <button
            className="login-button"
            onClick={handleLogin}
            disabled={isChecking}
          >
            {isChecking ? 'Checking...' : 'Sign in with Google'}
          </button>
        </div>

        <a
          href="#system-info"
          className="absolute bottom-8 flex flex-col items-center gap-1 text-xs text-muted-foreground transition-colors hover:text-foreground"
          aria-label="Scroll to system information"
        >
          <span>System information</span>
          <ChevronDown className="h-5 w-5 animate-bounce" />
        </a>
      </section>

      <section
        id="system-info"
        className="flex min-h-screen scroll-mt-4 flex-col justify-center px-8 py-16"
      >
        <div className="mx-auto w-full max-w-[900px]">
          <h2 className="mb-6 text-center text-xl font-semibold">System information</h2>
          <AppInfoPanel />
        </div>
      </section>
      <DevLoginSwitcher />
    </div>
  )
}
