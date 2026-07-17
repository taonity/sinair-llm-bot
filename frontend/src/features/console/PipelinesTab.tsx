'use client'

import { Fragment, useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { consoleApi } from './api'
import { DataTab, type Column } from './DataTab'
import { formatTime } from './format'
import type { PipelineRun, PipelineStage, PipelineStageStatus } from './types'

/** Dot colour per stage status, kept semantic so the flow strip reads at a glance. */
const STATUS_DOT: Record<PipelineStageStatus, string> = {
  OK: 'bg-emerald-500',
  PASS: 'bg-sky-500',
  STOP: 'bg-red-500',
  SKIP: 'bg-muted-foreground/50',
  INFO: 'bg-slate-400',
}

function outcomeBadge(outcome: string) {
  const tone =
    outcome === 'REPLIED'
      ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-600'
      : outcome === 'SILENT'
        ? 'text-muted-foreground'
        : outcome === 'COOLDOWN' || outcome === 'MUTED'
          ? 'border-amber-500/40 bg-amber-500/10 text-amber-600'
          : 'border-sky-500/40 bg-sky-500/10 text-sky-600'
  return (
    <Badge variant="outline" className={cn('font-normal', tone)}>
      {outcome}
    </Badge>
  )
}

/** Compact, minimalistic left-to-right strip of the pipeline steps shown inline in the table. */
function PipelineFlow({ stages }: { stages: PipelineStage[] }) {
  if (stages.length === 0) return <span className="text-muted-foreground">—</span>
  return (
    <div className="flex flex-wrap items-center gap-x-1 gap-y-0.5">
      {stages.map((stage, i) => (
        <Fragment key={`${stage.key}-${i}`}>
          {i > 0 && <span className="text-border">›</span>}
          <span className="inline-flex items-center gap-1" title={stage.summary || stage.label}>
            <span className={cn('size-1.5 rounded-full', STATUS_DOT[stage.status] ?? 'bg-muted-foreground')} />
            <span className="text-xs text-muted-foreground">{stage.label}</span>
          </span>
        </Fragment>
      ))}
    </div>
  )
}

function fieldsSummary(stages: PipelineStage[]): string {
  return stages.map((s) => s.label).join(' › ')
}

const PIPELINE_COLUMNS: Column<PipelineRun>[] = [
  {
    key: 'createdAt',
    label: 'When',
    value: (r) => r.createdAt,
    render: (r) => formatTime(r.createdAt),
    cellClassName: 'overflow-hidden whitespace-nowrap text-muted-foreground tabular-nums',
    headClassName: 'w-[150px]',
    skeleton: 'w-[85%]',
  },
  {
    key: 'triggerSenderLogin',
    label: 'Trigger',
    value: (r) => r.triggerSenderLogin,
    cellClassName: 'truncate font-medium',
    headClassName: 'w-[130px]',
    searchKey: 'triggerSenderLogin',
  },
  {
    key: 'triggerText',
    label: 'Message',
    value: (r) => r.triggerText,
    render: (r) => <span className="line-clamp-2">{r.triggerText || '—'}</span>,
    cellClassName: 'whitespace-normal break-words leading-snug text-muted-foreground',
    searchKey: 'triggerText',
  },
  {
    key: 'outcome',
    label: 'Outcome',
    value: (r) => r.outcome,
    render: (r) => outcomeBadge(r.outcome),
    headClassName: 'w-[120px]',
    skeleton: 'h-5 w-16 rounded-full',
    searchKey: 'outcome',
  },
  {
    key: 'flow',
    label: 'Flow',
    value: (r) => fieldsSummary(r.stages),
    render: (r) => <PipelineFlow stages={r.stages} />,
    cellClassName: 'whitespace-normal',
    headClassName: 'w-[34%]',
  },
]

/** Full detail shown when a pipeline row is expanded: every stage, its flags, and any alternatives. */
function PipelineDetail({ run }: { run: PipelineRun }) {
  return (
    <div className="flex flex-col gap-3">
      {run.outcomeDetail && (
        <div className="text-xs text-muted-foreground">
          Outcome <span className="font-medium text-foreground/80">{run.outcome}</span> — {run.outcomeDetail}
        </div>
      )}
      <ol className="flex flex-col gap-3">
        {run.stages.map((stage, i) => (
          <StageRow key={`${stage.key}-${i}`} stage={stage} />
        ))}
      </ol>
    </div>
  )
}

function StageRow({ stage }: { stage: PipelineStage }) {
  const [showAlternatives, setShowAlternatives] = useState(false)
  const altCount = stage.alternatives.length
  return (
    <li className="flex gap-2">
      <span className={cn('mt-1.5 size-2 shrink-0 rounded-full', STATUS_DOT[stage.status] ?? 'bg-muted-foreground')} />
      <div className="flex min-w-0 flex-col gap-1.5">
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
          <span className="text-sm font-medium">{stage.label}</span>
          {stage.summary && <span className="text-xs text-muted-foreground">{stage.summary}</span>}
        </div>
        {stage.fields.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {stage.fields.map((f, idx) => (
              <span
                key={idx}
                className="rounded border bg-background px-1.5 py-0.5 text-[11px] text-muted-foreground"
              >
                <span className="font-medium text-foreground/80">{f.label}</span>
                {f.value ? `: ${f.value}` : ''}
              </span>
            ))}
          </div>
        )}
        {altCount > 0 && (
          <div className="flex flex-col gap-1.5">
            <Button
              variant="ghost"
              size="sm"
              className="h-6 w-fit gap-1 px-1 text-xs text-muted-foreground hover:text-foreground"
              aria-expanded={showAlternatives}
              onClick={() => setShowAlternatives((v) => !v)}
            >
              {showAlternatives ? <ChevronDown /> : <ChevronRight />}
              {showAlternatives ? 'Hide' : 'Show'} {altCount} alternative{altCount === 1 ? '' : 's'}
            </Button>
            {showAlternatives && (
              <div className="flex flex-col gap-1.5">
                {stage.alternatives.map((alt, idx) => (
                  <div
                    key={idx}
                    className={cn(
                      'rounded-md border px-2 py-1.5 text-xs',
                      alt.chosen ? 'border-emerald-500/40 bg-emerald-500/5' : 'bg-background',
                    )}
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      {alt.chosen && (
                        <Badge
                          variant="outline"
                          className="h-4 border-emerald-500/40 px-1 text-[10px] font-normal text-emerald-600"
                        >
                          chosen
                        </Badge>
                      )}
                      {alt.fields.map((f, fi) => (
                        <span key={fi} className="text-[10px] text-muted-foreground">
                          {f.label} <span className="font-medium text-foreground/80">{f.value}</span>
                        </span>
                      ))}
                    </div>
                    <p className="mt-1 whitespace-pre-wrap break-words leading-snug">{alt.text}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </li>
  )
}

export function PipelinesTab({
  canEdit,
  onError,
}: {
  canEdit: boolean
  onError: (message: string) => void
}) {
  return (
    <DataTab<PipelineRun>
      columns={PIPELINE_COLUMNS}
      rowKey={(r) => r.id}
      load={(page, size, q, field, direction) =>
        consoleApi.listPipelineRuns(page, size, q, field, direction)
      }
      locate={(r, size, direction) =>
        consoleApi.locatePipelineRun(r.id, size, direction).then((res) => res.page)
      }
      expand={(r) => <PipelineDetail run={r} />}
      roomAccessor={(r) => r.roomTarget}
      canEdit={canEdit}
      onDelete={(r) => consoleApi.deletePipelineRun(r.id)}
      emptyLabel="No pipeline runs yet."
      sortLabel="created time"
      onError={onError}
    />
  )
}
