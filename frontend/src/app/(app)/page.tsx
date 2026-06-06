'use client'

import { useEffect, useState } from 'react'
import { fetchAuthenticatedUserStatus, requestLogout } from '@/lib/auth'
import { getRuntimeConfig } from '@/lib/runtimeConfig'
import { getCookie } from '@/lib/cookies'
import ErrorNotification from '@/components/ErrorNotification'

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

  if (loading) {
    return (
      <div className="page-container">
        <p>Loading...</p>
      </div>
    )
  }

  return (
    <div className="page-container">
      {error && (
        <ErrorNotification message={error} onClose={() => setError(null)} />
      )}

      {hello && (
        <>
          <h1>{hello.message}</h1>
          <p>Email: {hello.email}</p>
          <button className="logout-button" onClick={handleLogout}>
            Log out
          </button>
          <hr style={{ marginTop: '1.5rem' }} />
          <p style={{ color: '#666', fontSize: '0.9rem' }}>
            This is a template project with Google OAuth2 authentication.
            Edit this page to start building your app.
          </p>
        </>
      )}
    </div>
  )
}
