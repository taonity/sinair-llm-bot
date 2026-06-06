import { afterEach, describe, expect, it, vi } from 'vitest'

import { checkBackendLiveness, fetchAuthenticatedUserStatus } from '@/lib/auth'
import { DEFAULT_NETWORK_ERROR_MESSAGE, DEFAULT_TIMEOUT_ERROR_MESSAGE } from '@/lib/clientApi'

vi.mock('@/lib/clientApi', () => ({
  DEFAULT_NETWORK_ERROR_MESSAGE: 'Unable to connect to the server. Please check your connection.',
  DEFAULT_TIMEOUT_ERROR_MESSAGE: 'Request timed out. Please try again.',
  fetchWithTimeout: vi.fn(),
}))

import { fetchWithTimeout } from '@/lib/clientApi'

const fetchWithTimeoutMock = vi.mocked(fetchWithTimeout)

afterEach(() => {
  vi.clearAllMocks()
})

describe('auth api', () => {
  it('returns authenticated status when hello endpoint succeeds', async () => {
    fetchWithTimeoutMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ message: 'hello' }), { status: 200 }),
    )

    const result = await fetchAuthenticatedUserStatus()
    expect(result.status).toBe('authenticated')
  })

  it('returns timeout error for 504 response', async () => {
    fetchWithTimeoutMock.mockResolvedValueOnce(new Response(null, { status: 504 }))

    await expect(fetchAuthenticatedUserStatus()).resolves.toEqual({
      status: 'error',
      message: DEFAULT_TIMEOUT_ERROR_MESSAGE,
    })
  })

  it('returns unauthenticated status for non-ok response', async () => {
    fetchWithTimeoutMock.mockResolvedValueOnce(new Response(null, { status: 401 }))

    await expect(fetchAuthenticatedUserStatus()).resolves.toEqual({
      status: 'unauthenticated',
      httpStatus: 401,
    })
  })

  it('returns network error when request throws', async () => {
    fetchWithTimeoutMock.mockRejectedValueOnce(new Error('boom'))

    await expect(fetchAuthenticatedUserStatus()).resolves.toEqual({
      status: 'error',
      message: DEFAULT_NETWORK_ERROR_MESSAGE,
    })
  })

  it('returns liveness success when backend is up', async () => {
    fetchWithTimeoutMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 'UP' }), { status: 200 }),
    )

    await expect(checkBackendLiveness()).resolves.toEqual({ ok: true })
  })

  it('returns timeout error for 504 liveness response', async () => {
    fetchWithTimeoutMock.mockResolvedValueOnce(new Response(null, { status: 504 }))

    await expect(checkBackendLiveness()).resolves.toEqual({
      ok: false,
      message: DEFAULT_TIMEOUT_ERROR_MESSAGE,
    })
  })

  it('returns error when backend is not up', async () => {
    fetchWithTimeoutMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 'DOWN' }), { status: 200 }),
    )

    await expect(checkBackendLiveness()).resolves.toMatchObject({
      ok: false,
      message: 'Backend server is currently unavailable. Please try again later.',
    })
  })

  it('returns network error when liveness request throws', async () => {
    fetchWithTimeoutMock.mockRejectedValueOnce(new Error('boom'))

    await expect(checkBackendLiveness()).resolves.toEqual({
      ok: false,
      message: DEFAULT_NETWORK_ERROR_MESSAGE,
    })
  })
})
