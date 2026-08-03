'use client'

import { Monitor, Moon, Sun } from 'lucide-react'
import { usePathname } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { useTheme, type Theme } from './ThemeProvider'

const ORDER: Theme[] = ['system', 'light', 'dark']

const NEXT_LABEL: Record<Theme, string> = {
  system: 'Switch to light theme',
  light: 'Switch to dark theme',
  dark: 'Switch to system theme',
}

export function ThemeToggle({ className, hideOnPayload = false }: { className?: string; hideOnPayload?: boolean }) {
  const { theme, setTheme } = useTheme()
  const pathname = usePathname()

  if (hideOnPayload && pathname.startsWith('/view/payload/')) return null

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
