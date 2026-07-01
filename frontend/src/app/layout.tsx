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
          <ThemeToggle className="fixed right-3 top-3 z-50" />
          <main>{children}</main>
        </ThemeProvider>
      </body>
    </html>
  )
}
