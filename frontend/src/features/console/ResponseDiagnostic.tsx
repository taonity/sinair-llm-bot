'use client'

import { AlertTriangle, Check, Clock, Send, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { formatTime } from './format'
import { responseDiagnostic } from './pipelineDiagnostics'
import type { BotResponseState } from './types'

export function ResponseDiagnostic({ state }: { state?: BotResponseState | null }) {
  const diagnostic = responseDiagnostic(state)
  if (!state || !diagnostic) return null
  const Icon = state.status === 'REPLIED' ? Check
    : state.status === 'DISCARDED' ? X
      : state.status === 'FAILED' ? AlertTriangle
        : state.status === 'REPLY_QUEUED' ? Send : Clock
  const tone = state.status === 'REPLIED' ? 'text-emerald-600'
    : state.status === 'FAILED' ? 'text-red-600'
      : state.status === 'DISCARDED' ? 'text-muted-foreground' : 'text-amber-700 dark:text-amber-400'

  return (
    <section aria-label="Message response" className="w-full min-w-0 max-w-[calc(100vw-5rem)] whitespace-normal text-xs leading-snug sm:max-w-full">
      <h3 className={cn('flex items-start gap-1 font-medium', tone)}>
        <Icon aria-hidden="true" className="mt-0.5 size-3 shrink-0" />
        <span className="min-w-0 break-words">{diagnostic.label}</span>
      </h3>
      <p className="mt-0.5 break-words text-muted-foreground">{diagnostic.reason}</p>
      <div className="mt-2 flex min-w-0 flex-col gap-1 break-words border-l-2 border-border pl-2 text-muted-foreground">
        {state.detail && <p className="text-foreground">{state.detail}</p>}
        {state.category && <p>Category: {state.category.replaceAll('_', ' ')}</p>}
        {state.deferredAt && <p>First deferred: {formatTime(state.deferredAt)}</p>}
        {state.nextAttemptAt && <p>Next check: {formatTime(state.nextAttemptAt)}</p>}
        <p>Updated: {formatTime(state.updatedAt)}</p>
        {state.outboundMessageId && (
          <a className="text-sky-600 underline underline-offset-2" href={`?outbound=${encodeURIComponent(state.outboundMessageId)}`}>
            View {state.reason === 'GENERATION_FAILED' ? 'error notice' : 'reply'}
          </a>
        )}
      </div>
    </section>
  )
}