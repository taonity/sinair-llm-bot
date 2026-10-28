import './globals.css'
import type { Metadata } from 'next'
import { Geist } from "next/font/google";
import { cn } from "@/lib/utils";
import { ThemeProvider, themeInitScript } from "@/components/theme/ThemeProvider";
import { ThemeToggle } from "@/components/theme/ThemeToggle";

const geist = Geist({subsets:['latin'],variable:'--font-sans'});

export const metadata: Metadata = {
  title: 'Sinair LLM Bot',
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
