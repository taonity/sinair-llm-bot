'use client'

import { useCallback, useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { consoleApi } from './api'
import { formatTime } from './format'
import type { RoomSummary } from './types'

export function SummariesTab({
  canEdit,
  onError,
}: {
  canEdit: boolean
  onError: (message: string) => void
}) {
  const [summaries, setSummaries] = useState<RoomSummary[] | null>(null)
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [savingId, setSavingId] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const result = await consoleApi.listSummaries()
      setSummaries(result)
      setDrafts(Object.fromEntries(result.map((s) => [s.id, s.summary])))
    } catch {
      onError('Failed to load summaries.')
    }
  }, [onError])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (summary: RoomSummary) => {
    setSavingId(summary.id)
    try {
      const updated = await consoleApi.updateSummary(summary.id, drafts[summary.id] ?? '')
      if (updated) {
        setSummaries((prev) => (prev ?? []).map((s) => (s.id === updated.id ? updated : s)))
      }
    } catch {
      onError('Failed to save summary.')
    } finally {
      setSavingId(null)
    }
  }

  if (!summaries) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-7 w-full max-w-xs" />
        {Array.from({ length: 2 }).map((_, i) => (
          <Card key={i}>
            <CardHeader className="flex flex-row items-center justify-between gap-2 pb-2">
              <Skeleton className="h-4 w-28" />
              <Skeleton className="h-3 w-44" />
            </CardHeader>
            <CardContent className="flex flex-col gap-2">
              <Skeleton className="h-[300px] w-full" />
              <Skeleton className="h-8 w-16" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {summaries.length === 0 && (
        <p className="text-sm text-muted-foreground">No summaries yet.</p>
      )}

      {summaries.map((s) => {
        const dirty = (drafts[s.id] ?? '') !== s.summary
        return (
          <Card key={s.id}>
            <CardHeader className="flex flex-row items-center justify-between gap-2 pb-2">
              <span className="font-medium">{s.roomTarget}</span>
              <span className="text-xs text-muted-foreground">
                {s.messageCount} messages · updated {formatTime(s.updatedAt)}
              </span>
            </CardHeader>
            <CardContent className="flex flex-col gap-2">
              <Textarea
                value={drafts[s.id] ?? ''}
                readOnly={!canEdit}
                className="min-h-[300px] resize-y font-mono text-xs"
                onChange={(e) => setDrafts((prev) => ({ ...prev, [s.id]: e.target.value }))}
              />
              {canEdit && (
                <div className="flex gap-2">
                  <Button size="sm" disabled={!dirty || savingId === s.id} onClick={() => save(s)}>
                    {savingId === s.id ? 'Saving…' : 'Save'}
                  </Button>
                  {dirty && (
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setDrafts((prev) => ({ ...prev, [s.id]: s.summary }))}
                    >
                      Reset
                    </Button>
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}
