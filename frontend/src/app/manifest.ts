import type { MetadataRoute } from 'next'
import { getAppIdentity } from '@/lib/appIdentity'

export const dynamic = 'force-dynamic'

export default function manifest(): MetadataRoute.Manifest {
  const identity = getAppIdentity()

  return {
    id: '/',
    name: identity.name,
    description: 'Sinair LLM Bot data console',
    lang: 'en',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    background_color: '#000000',
    theme_color: '#000000',
    prefer_related_applications: false,
    icons: [
      { src: `${identity.iconPrefix}-192.png`, sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: `${identity.iconPrefix}-512.png`, sizes: '512x512', type: 'image/png', purpose: 'any' },
      { src: `${identity.iconPrefix}-maskable-512.png`, sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  }
}
