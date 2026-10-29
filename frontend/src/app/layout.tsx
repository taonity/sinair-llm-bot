import './globals.css'
import type { Metadata, Viewport } from 'next'
import { connection } from 'next/server'
import { getAppIdentity } from '@/lib/appIdentity'
import { Geist } from "next/font/google";
import { cn } from "@/lib/utils";
import { ThemeProvider, themeInitScript } from "@/components/theme/ThemeProvider";
import { ThemeToggle } from "@/components/theme/ThemeToggle";

const geist = Geist({subsets:['latin'],variable:'--font-sans'});

export async function generateMetadata(): Promise<Metadata> {
  // The same image runs in stage and production, so branding must be resolved at runtime.
  await connection()
  const identity = getAppIdentity()

  return {
    title: identity.name,
    applicationName: identity.name,
    manifest: '/manifest.webmanifest',
    icons: {
      icon: [{ url: identity.favicon, sizes: '16x16 32x32 48x48' }],
      apple: [{ url: `${identity.iconPrefix}-180.png`, sizes: '180x180', type: 'image/png' }],
    },
    appleWebApp: {
      capable: true,
      title: identity.name,
      statusBarStyle: 'default',
    },
  }
}

export const viewport: Viewport = {
  themeColor: '#000000',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode,
}) {
  return (
    <html lang="en" suppressHydrationWarning className={cn("font-sans", geist.variable)}>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body>
        <ThemeProvider>
          <ThemeToggle className="absolute right-3 top-2 z-50 min-h-11 min-w-11 sm:top-3 sm:min-h-0 sm:min-w-0" hideOnPayload />
          <main>{children}</main>
        </ThemeProvider>
      </body>
    </html>
  )
}
