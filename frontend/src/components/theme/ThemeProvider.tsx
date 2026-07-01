'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

export type Theme = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'theme'

type ThemeContextValue = {
  /** The user's selection: an explicit theme or "system" (follow OS/browser). */
  theme: Theme
  /** The theme actually applied after resolving "system". */
  resolvedTheme: ResolvedTheme
  setTheme: (theme: Theme) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function prefersDark(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
  )
}

function resolve(theme: Theme): ResolvedTheme {
  if (theme === 'system') return prefersDark() ? 'dark' : 'light'
  return theme
}

/**
 * Applies a switchable light/dark theme by toggling the `dark` class on <html>.
 *
 * The selection is persisted in localStorage and defaults to "system", which follows the
 * OS/browser `prefers-color-scheme`. A blocking inline script in the document head (see
 * {@link themeInitScript}) applies the correct class before hydration to avoid a flash.
 */
export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<Theme>('system')
  const [resolvedTheme, setResolvedTheme] = useState<ResolvedTheme>('light')

  const apply = useCallback((next: Theme) => {
    const resolved = resolve(next)
    setResolvedTheme(resolved)
    document.documentElement.classList.toggle('dark', resolved === 'dark')
  }, [])

  // Load the stored preference once on mount.
  useEffect(() => {
    const stored = localStorage.getItem(THEME_STORAGE_KEY) as Theme | null
    const initial: Theme =
      stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system'
    setThemeState(initial)
    apply(initial)
  }, [apply])

  // Keep in sync with the OS/browser while the selection is "system".
  useEffect(() => {
    if (theme !== 'system') return
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => apply('system')
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [theme, apply])

  const setTheme = useCallback(
    (next: Theme) => {
      localStorage.setItem(THEME_STORAGE_KEY, next)
      setThemeState(next)
      apply(next)
    },
    [apply],
  )

  const value = useMemo(
    () => ({ theme, resolvedTheme, setTheme }),
    [theme, resolvedTheme, setTheme],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used within a ThemeProvider')
  return ctx
}

/**
 * Inline script that runs before React hydrates to set the initial `dark` class from the
 * stored preference (or OS setting), preventing a light/dark flash on first paint.
 */
export const themeInitScript = `(function(){try{var t=localStorage.getItem('${THEME_STORAGE_KEY}');var d=t==='dark'||((t==='system'||!t)&&window.matchMedia('(prefers-color-scheme: dark)').matches);document.documentElement.classList.toggle('dark',d);}catch(e){}})();`
