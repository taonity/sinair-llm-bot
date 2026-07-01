'use client'

import { Monitor, Moon, Sun } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTheme, type Theme } from './ThemeProvider'

const ORDER: Theme[] = ['system', 'light', 'dark']

const NEXT_LABEL: Record<Theme, string> = {
  system: 'Switch to light theme',
  light: 'Switch to dark theme',
  dark: 'Switch to system theme',
}

/**
 * A single button that cycles the theme selection: system → light → dark → system.
 * The icon reflects the current selection (monitor for system, sun for light, moon for dark).
 */
export function ThemeToggle({ className }: { className?: string }) {
  const { theme, setTheme } = useTheme()

  const cycle = () => {
    const currentIndex = ORDER.indexOf(theme)
    const next = ORDER[(currentIndex + 1) % ORDER.length] ?? 'system'
    setTheme(next)
  }

  return (
    <Button
      variant="ghost"
      size="icon-sm"
      className={className}
      onClick={cycle}
      aria-label={NEXT_LABEL[theme]}
      title={`Theme: ${theme}`}
    >
      {theme === 'system' ? <Monitor /> : theme === 'light' ? <Sun /> : <Moon />}
    </Button>
  )
}
