import {
  DEFAULT_NETWORK_ERROR_MESSAGE,
  DEFAULT_TIMEOUT_ERROR_MESSAGE,
  fetchWithTimeout,
} from '@/lib/clientApi'

export async function fetchAuthenticatedUserStatus() {
  try {
    const response = await fetchWithTimeout('/api/hello', { timeoutMs: 6000 })

    if (response.ok) {
      return { status: 'authenticated' as const, data: await response.json() }
    }

    if (response.status === 504) {
      return {
        status: 'error' as const,
        message: DEFAULT_TIMEOUT_ERROR_MESSAGE,
      }
    }

    return {
      status: 'unauthenticated' as const,
      httpStatus: response.status,
    }
  } catch {
    return {
      status: 'error' as const,
      message: DEFAULT_NETWORK_ERROR_MESSAGE,
    }
  }
}

export async function checkBackendLiveness() {
  try {
    const response = await fetchWithTimeout('/api/actuator/health/liveness', { timeoutMs: 6000 })

    if (response.status === 504) {
      return {
        ok: false as const,
        message: DEFAULT_TIMEOUT_ERROR_MESSAGE,
      }
    }

    const data = await response.json()

    if (!response.ok || data.status !== 'UP') {
      return {
        ok: false as const,
        message: 'Backend server is currently unavailable. Please try again later.',
        status: response.status,
        data,
      }
    }

    return {
      ok: true as const,
    }
  } catch {
    return {
      ok: false as const,
      message: DEFAULT_NETWORK_ERROR_MESSAGE,
    }
  }
}

export async function requestLogout(xsrfToken: string) {
  const response = await fetchWithTimeout('/api/logout', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': xsrfToken },
    timeoutMs: 6000,
  })

  if (!response.ok) {
    throw new Error(`Logout failed with status ${response.status}`)
  }
}
