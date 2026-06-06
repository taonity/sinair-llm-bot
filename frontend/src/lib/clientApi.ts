export const DEFAULT_TIMEOUT_ERROR_MESSAGE = 'Request timed out. Please try again.'
export const DEFAULT_NETWORK_ERROR_MESSAGE =
  'Unable to connect to the server. Please check your connection.'

type FetchWithTimeoutInit = RequestInit & {
  timeoutMs?: number
}

export async function fetchWithTimeout(
  input: RequestInfo | URL,
  init: FetchWithTimeoutInit = {},
): Promise<Response> {
  const { timeoutMs = 10000, credentials = 'include', ...fetchInit } = init
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)

  try {
    return await fetch(input, {
      ...fetchInit,
      credentials,
      signal: controller.signal,
    })
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      return new Response(null, { status: 504 })
    }

    throw error
  } finally {
    clearTimeout(timeoutId)
  }
}
