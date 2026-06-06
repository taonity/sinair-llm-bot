let configCache: { csrfCookieName: string; publicBackendUrl: string } | null = null

export async function getRuntimeConfig() {
  if (configCache) {
    return configCache
  }
  try {
    const response = await fetch('/api/config', { cache: 'no-store' })
    configCache = await response.json()
    return configCache!
  } catch (error) {
    console.error('Failed to load runtime config:', error)
    return {
      csrfCookieName: '',
      publicBackendUrl: '',
    }
  }
}
