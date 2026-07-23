'use client'

import { Fragment, useState } from 'react'
import { AlertTriangle, ChevronDown, ChevronRight } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { consoleApi } from './api'
import { DataTab, type Column } from './DataTab'
import { formatTime } from './format'
import type { JsonParseFailure, LlmCallUsage, PipelineRun, PipelineStage, PipelineStageStatus } from './types'

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
    outcome === 'REPLIED' || outcome === 'SUMMARY_REFRESHED'
      ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-600'
      : outcome === 'SILENT'
        ? 'text-muted-foreground'
        : outcome === 'SUMMARY_FAILED'
          ? 'border-red-500/40 bg-red-500/10 text-red-600'
          : outcome === 'COOLDOWN' || outcome === 'MUTED'
            ? 'border-amber-500/40 bg-amber-500/10 text-amber-600'
            : 'border-sky-500/40 bg-sky-500/10 text-sky-600'
  return (
    <Badge variant="outline" className={cn('font-normal whitespace-nowrap', tone)}>
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

/** Drops the provider prefix ("openai/gpt-4o-mini" -> "gpt-4o-mini") for a compact model label. */
function shortModel(model: string): string {
  const slash = model.lastIndexOf('/')
  return slash >= 0 ? model.slice(slash + 1) : model
}

const PIPELINE_COLUMNS: Column<PipelineRun>[] = [
  {
    key: 'createdAt',
    label: 'When',
    value: (r) => r.createdAt,
    render: (r) => formatTime(r.createdAt),
    cellClassName: 'whitespace-nowrap text-muted-foreground tabular-nums',
    headClassName: 'w-[160px]',
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
    render: (r) => (
      <div className="flex flex-wrap items-center gap-1">
        {outcomeBadge(r.outcome)}
        {r.jsonParseFailures.length > 0 && (
          <Badge
            variant="outline"
            className="gap-1 border-amber-500/40 bg-amber-500/10 font-normal text-amber-600"
            title={`${r.jsonParseFailures.length} JSON parse failure(s) — the model returned unparseable JSON and the prompt was retried. Expand the row to inspect the payloads.`}
          >
            <AlertTriangle className="size-3" />
            {r.jsonParseFailures.length}
          </Badge>
        )}
      </div>
    ),
    headClassName: 'w-[160px]',
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

/** Lists the JSON-parse failures of a run: how many times a JSON prompt returned unparseable
 * output (each one a retry) and the exact offending payload, in a collapsible spoiler. */
function JsonFailures({ failures }: { failures: JsonParseFailure[] }) {
  return (
    <div className="flex flex-col gap-1.5 rounded-md border border-amber-500/40 bg-amber-500/5 px-2 py-1.5">
      <div className="flex items-center gap-1.5 text-xs font-medium text-amber-600">
        <AlertTriangle className="size-3.5" />
        {failures.length} JSON parse failure{failures.length === 1 ? '' : 's'} — retried on unparseable output
      </div>
      <div className="flex flex-col gap-1">
        {failures.map((f, i) => (
          <details key={i} className="group text-xs">
            <summary className="flex cursor-pointer list-none items-center gap-1 text-muted-foreground marker:hidden hover:text-foreground">
              <ChevronRight className="size-3 transition-transform group-open:rotate-90" />
              <span className="font-medium text-foreground/80">{f.label}</span>
              <span>attempt {f.attempt}</span>
            </summary>
            <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap break-words rounded border bg-background px-2 py-1.5 text-[11px] leading-snug text-muted-foreground">
              {f.payload || '<empty>'}
            </pre>
          </details>
        ))}
      </div>
    </div>
  )
}

/** One LLM call paired with its original index in run.llmUsage (needed for the raw payload links). */
type UsageEntry = { call: LlmCallUsage; index: number }

/** Groups contiguous calls made on the same tier (e.g. the repo tool loop's rounds) so multi-round
 * stages collapse into a single compact block instead of a long list of near-identical chips. */
function groupUsage(usage: LlmCallUsage[]): { tier: string; entries: UsageEntry[] }[] {
  const groups: { tier: string; entries: UsageEntry[] }[] = []
  usage.forEach((call, index) => {
    const last = groups[groups.length - 1]
    if (last && last.tier === call.tier) last.entries.push({ call, index })
    else groups.push({ tier: call.tier, entries: [{ call, index }] })
  })
  return groups
}

/** The "request"/"response" raw-payload links for a single call, keyed by its original index. */
function CallLinks({ runId, index, call }: { runId: string; index: number; call: LlmCallUsage }) {
  return (
    <>
      {call.hasRequestPayload && (
        <a
          href={`/api/console/pipeline-runs/${encodeURIComponent(runId)}/llm-usage/${index}/request`}
          target="_blank"
          rel="noreferrer"
          className="text-sky-600 underline underline-offset-2 hover:text-sky-700"
        >
          request
        </a>
      )}
      {call.hasResponsePayload && (
        <a
          href={`/api/console/pipeline-runs/${encodeURIComponent(runId)}/llm-usage/${index}/response`}
          target="_blank"
          rel="noreferrer"
          className="text-sky-600 underline underline-offset-2 hover:text-sky-700"
        >
          response
        </a>
      )}
    </>
  )
}

/** Compact chip for a single-call tier (gate, triage, reply, critic…). */
function UsageChip({ runId, entry }: { runId: string; entry: UsageEntry }) {
  const { call, index } = entry
  return (
    <span className="inline-flex items-center gap-1.5 rounded border bg-background px-1.5 py-0.5 text-[11px] text-muted-foreground">
      <span className="font-medium text-foreground/80">{call.tier}</span>
      <span>{call.model}</span>
      <span className="tabular-nums">{call.tokens.toLocaleString()} tok</span>
      {call.tools.length > 0 && (
        <span className="rounded bg-sky-500/10 px-1 text-sky-600">{call.tools.join(', ')}</span>
      )}
      <CallLinks runId={runId} index={index} call={call} />
    </span>
  )
}

/** Collapsible block for a multi-round tier: header sums the rounds' tokens; expanding reveals every
 * round's own tokens, tools and raw-payload links so no detail is lost. */
function UsageGroup({ runId, tier, entries }: { runId: string; tier: string; entries: UsageEntry[] }) {
  const [open, setOpen] = useState(false)
  const tokens = entries.reduce((sum, e) => sum + e.call.tokens, 0)
  const models = Array.from(new Set(entries.map((e) => e.call.model)))
  const tools = Array.from(new Set(entries.flatMap((e) => e.call.tools)))
  return (
    <div className="flex w-full flex-col gap-1 rounded border bg-background px-1.5 py-1 text-[11px] text-muted-foreground">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex flex-wrap items-center gap-1.5 text-left"
      >
        {open ? <ChevronDown className="size-3" /> : <ChevronRight className="size-3" />}
        <span className="font-medium text-foreground/80">
          {tier} × {entries.length}
        </span>
        <span>{models.length === 1 ? models[0] : `${models.length} models`}</span>
        <span className="tabular-nums">Σ {tokens.toLocaleString()} tok</span>
        {tools.length > 0 && (
          <span className="rounded bg-sky-500/10 px-1 text-sky-600">{tools.join(', ')}</span>
        )}
      </button>
      {open && (
        <ol className="ml-4 flex flex-col gap-1 border-l pl-2">
          {entries.map((e, i) => (
            <li key={e.index} className="flex flex-wrap items-center gap-1.5">
              <span className="tabular-nums text-foreground/50">#{i + 1}</span>
              {models.length > 1 && <span>{e.call.model}</span>}
              <span className="tabular-nums">{e.call.tokens.toLocaleString()} tok</span>
              {e.call.tools.length > 0 && (
                <span className="rounded bg-sky-500/10 px-1 text-sky-600">{e.call.tools.join(', ')}</span>
              )}
              <CallLinks runId={runId} index={e.index} call={e.call} />
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

/** Full detail shown when a pipeline row is expanded: every stage, its flags, and any alternatives. */
function PipelineDetail({ run }: { run: PipelineRun }) {
  return (
    <div className="flex flex-col gap-3">
      {run.outcomeDetail && (
        <div className="text-xs text-muted-foreground">
          Outcome <span className="font-medium text-foreground/80">{run.outcome}</span> — {run.outcomeDetail}
        </div>
      )}
      {run.jsonParseFailures.length > 0 && <JsonFailures failures={run.jsonParseFailures} />}
      {run.llmUsage.length > 0 && (
        <div className="flex flex-col gap-1.5">
          <div className="text-xs font-medium">
            LLM usage · {run.totalTokens.toLocaleString()} tokens total
          </div>
          <div className="flex flex-wrap gap-1.5">
            {groupUsage(run.llmUsage).map((group, gi) =>
              group.entries.length === 1 ? (
                <UsageChip key={gi} runId={run.id} entry={group.entries[0]!} />
              ) : (
                <UsageGroup key={gi} runId={run.id} tier={group.tier} entries={group.entries} />
              ),
            )}
          </div>
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
  onError,
}: {
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
      emptyLabel="No pipeline runs yet."
      sortLabel="created time"
      onError={onError}
    />
  )
}
