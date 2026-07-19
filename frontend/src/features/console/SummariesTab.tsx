'use client'

import { useCallback, useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { consoleApi } from './api'
import { formatTime } from './format'
import type { RoomSummary, SummaryVersion } from './types'

export function SummariesTab({
  canEdit,
  onError,
}: {
  canEdit: boolean
  onError: (message: string) => void
}) {
  const [summaries, setSummaries] = useState<RoomSummary[] | null>(null)
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const result = await consoleApi.listSummaries()
      setSummaries(result)
      setDrafts(
        Object.fromEntries(
          result.flatMap((s) => [
            [s.id, s.summary] as const,
            ...s.history.map((h) => [h.id, h.summary] as const),
          ]),
        ),
      )
    } catch {
      onError('Failed to load summaries.')
    }
  }, [onError])

  useEffect(() => {
    void load()
  }, [load])

  const save = async (id: string) => {
    setBusyId(id)
    try {
      await consoleApi.updateSummary(id, drafts[id] ?? '')
      await load()
    } catch {
      onError('Failed to save summary.')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (id: string) => {
    if (typeof window !== 'undefined' && !window.confirm('Delete this summary version?')) return
    setBusyId(id)
    try {
      await consoleApi.deleteSummary(id)
      await load()
    } catch {
      onError('Failed to delete summary.')
    } finally {
      setBusyId(null)
    }
  }

  const editor = (
    id: string,
    original: string,
    pipelineRunId: string | null,
    detailAvailable: boolean,
    minHeight: string,
  ) => {
    const dirty = (drafts[id] ?? '') !== original
    return (
      <div className="flex flex-col gap-2">
        <Textarea
          value={drafts[id] ?? ''}
          readOnly={!canEdit}
          className={`${minHeight} resize-y font-mono text-xs`}
          onChange={(e) => setDrafts((prev) => ({ ...prev, [id]: e.target.value }))}
        />
        <div className="flex flex-wrap items-center gap-2">
          {canEdit && (
            <>
              <Button size="sm" disabled={!dirty || busyId === id} onClick={() => save(id)}>
                {busyId === id ? 'Saving…' : 'Save'}
              </Button>
              {dirty && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setDrafts((prev) => ({ ...prev, [id]: original }))}
                >
                  Reset
                </Button>
              )}
              <Button
                size="sm"
                variant="ghost"
                className="text-destructive hover:text-destructive"
                disabled={busyId === id}
                onClick={() => remove(id)}
              >
                Delete
              </Button>
            </>
          )}
          <SourceLink pipelineRunId={pipelineRunId} detailAvailable={detailAvailable} />
        </div>
      </div>
    )
  }

  if (!summaries) {
    return (
      <div className="flex flex-col gap-3">
        <Card>
          <CardHeader className="flex flex-col gap-1 pb-2 sm:flex-row sm:items-center sm:justify-between sm:gap-2">
            <Skeleton className="h-5 w-28" />
            <Skeleton className="h-3 w-44" />
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            <Textarea readOnly value="" className="min-h-[300px] resize-y font-mono text-xs" />
            {canEdit && <Skeleton className="h-8 w-16" />}
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {summaries.length === 0 && (
        <p className="text-sm text-muted-foreground">No summaries yet.</p>
      )}

      {summaries.map((s) => (
        <Card key={s.id}>
          <CardHeader className="flex flex-col gap-1 pb-2 sm:flex-row sm:items-center sm:justify-between sm:gap-2">
            <span className="font-medium">{s.roomTarget}</span>
            <span className="text-xs text-muted-foreground">
              {s.messageCount} messages · updated {formatTime(s.updatedAt)}
            </span>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {editor(s.id, s.summary, s.pipelineRunId, s.detailAvailable, 'min-h-[300px]')}

            {s.history.length > 0 && (
              <div className="flex flex-col gap-1 pt-1">
                <span className="text-xs font-medium text-muted-foreground">
                  Previous versions ({s.history.length})
                </span>
                {s.history.map((h: SummaryVersion) => (
                  <details key={h.id} className="rounded-md border bg-muted/30 px-2 py-1">
                    <summary className="cursor-pointer select-none text-xs text-muted-foreground">
                      {formatTime(h.createdAt)} · {h.messageCount} messages · {h.summary.length} chars
                    </summary>
                    <div className="mt-2">
                      {editor(h.id, h.summary, h.pipelineRunId, h.detailAvailable, 'min-h-[200px]')}
                    </div>
                  </details>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function SourceLink({
  pipelineRunId,
  detailAvailable,
}: {
  pipelineRunId: string | null
  detailAvailable: boolean
}) {
  if (pipelineRunId && detailAvailable) {
    return (
      <a
        href={`/api/console/pipeline-runs/${encodeURIComponent(pipelineRunId)}/llm-usage/0/request`}
        target="_blank"
        rel="noreferrer"
        className="text-xs text-sky-600 underline underline-offset-2 hover:text-sky-500"
      >
        source transcript
      </a>
    )
  }
  return (
    <span className="text-xs text-muted-foreground">source messages purged (7-day retention)</span>
  )
}
