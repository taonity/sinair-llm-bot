import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { fetchWithTimeout, DEFAULT_NETWORK_ERROR_MESSAGE, DEFAULT_TIMEOUT_ERROR_MESSAGE } from '@/lib/clientApi'
import type { User } from '@/types/user'
import keysToCamel from '@/utils/objectCase'

interface UseUserOptions {
  onError?: React.Dispatch<React.SetStateAction<string | null>>
  silent?: boolean
}

export function useUser({ onError, silent = false }: UseUserOptions = {}) {
  const [user, setUser] = useState<User | null>(null)
  const router = useRouter()

  useEffect(() => {
    let isActive = true

    async function loadUser() {
      try {
        const response = await fetchWithTimeout('/api/user', { timeoutMs: 6000 })

        if (!isActive) {
          return
        }

        if (response.status === 401) {
          if (!silent) {
            router.replace('/login')
          }
          return
        }

        if (response.status === 504) {
          if (!silent) {
            onError?.(DEFAULT_TIMEOUT_ERROR_MESSAGE)
          }
          return
        }

        if (!response.ok) {
          if (!silent) {
            router.replace('/login')
          }
          return
        }

        const responseBody = await response.json()

        if (isActive) {
          setUser(keysToCamel(responseBody) as User)
        }
      } catch {
        if (isActive && !silent) {
          onError?.(DEFAULT_NETWORK_ERROR_MESSAGE)
        }
      }
    }

    void loadUser()

    return () => {
      isActive = false
    }
  }, [onError, router, silent])

  return user
}
