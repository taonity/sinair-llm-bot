'use client'

import { useEffect, useRef, useState } from 'react'
import { AlertTriangle, BookOpen, ChevronDown, ChevronRight, Cpu, Download, Maximize2, Minimize2, Wrench } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'
import { consoleApi } from './api'
import { DataTab, type Column } from './DataTab'
import { formatTime, formatTokens } from './format'
import { pipelineDiagnostics, pipelineOutcomeReason, type PipelineDiagnostic } from './pipelineDiagnostics'
import type {
  JsonParseFailure,
  LlmCallUsage,
  PipelineRun,
  PipelineStage,
  PipelineStageStatus,
  ToolCallEntry,
} from './types'

const STATUS_DOT: Record<PipelineStageStatus, string> = {
  OK: 'bg-emerald-500',
  PASS: 'bg-sky-500',
  STOP: 'bg-red-500',
  SKIP: 'bg-muted-foreground/50',
  INFO: 'bg-slate-400',
}
function outcomeBadge(outcome: string, clarification?: string | null) {
  const tone =
    outcome === 'REPLIED' || outcome === 'SUMMARY_REFRESHED'
      ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-600'
      : outcome === 'SILENT'
        ? 'text-muted-foreground'
        : outcome === 'FAILED' || outcome === 'SUMMARY_FAILED'
          ? 'border-red-500/40 bg-red-500/10 text-red-600'
          : outcome === 'COOLDOWN' || outcome === 'MUTED'
            ? 'border-amber-500/40 bg-amber-500/10 text-amber-600'
            : 'border-sky-500/40 bg-sky-500/10 text-sky-600'
  return (
    <Badge variant="outline" className={cn('font-normal whitespace-nowrap', tone)} title={clarification ?? undefined}>
      {outcome}
    </Badge>
  )
}

function shortModel(model: string): string {
  const slash = model.lastIndexOf('/')
  return slash >= 0 ? model.slice(slash + 1) : model
}

function TokenSplit({
  prompt,
  completion,
  total,
  prefix,
}: {
  prompt: number
  completion: number
  total: number
  prefix?: string
}) {
  const hasSplit = prompt > 0 || completion > 0
  if (!hasSplit) {
    return (
      <span className="tabular-nums">
        {prefix ? `${prefix} ` : ''}
        {formatTokens(total)} tok
      </span>
    )
  }
  return (
    <span className="tabular-nums" title={`${prompt.toLocaleString()} in · ${completion.toLocaleString()} out`}>
      {prefix ? <span className="text-foreground/50">{prefix} </span> : null}
      <span className="text-sky-600/80">↓{formatTokens(prompt)}</span>{' '}
      <span className="text-emerald-600/80">↑{formatTokens(completion)}</span>
    </span>
  )
}

const pipelineColumns = (
  onDiagnostic: (run: PipelineRun, diagnostic: PipelineDiagnostic) => void,
): Column<PipelineRun>[] => [
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
    render: (run) => (
      <div className="flex items-center gap-1">
        {outcomeBadge(run.outcome, pipelineOutcomeReason(run))}
        <a
          href={`/api/console/pipeline-runs/${encodeURIComponent(run.id)}/export`}
          download
          title="Export debug bundle"
          aria-label="Export debug bundle"
          className={cn(
            buttonVariants({ variant: 'ghost', size: 'icon-xs' }),
            'shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover/data-row:opacity-100 group-focus-within/data-row:opacity-100 focus-visible:opacity-100',
          )}
        >
          <Download />
        </a>
      </div>
    ),
    cellClassName: 'whitespace-normal',
    headClassName: 'w-[200px]',
    skeleton: 'h-5 w-16 rounded-full',
    searchKey: 'outcome',
  },
  {
    key: 'diagnostics',
    label: 'Diagnostics',
    value: (run) => pipelineDiagnostics(run).map((diagnostic) => diagnostic.label).join(', '),
    render: (run, actions) => (
      <div className="flex flex-col items-start gap-1">
        {pipelineDiagnostics(run).map((diagnostic) => (
          <button
            key={diagnostic.kind}
            type="button"
            title={`Inspect ${diagnostic.label}`}
            onClick={() => {
              actions.expand()
              onDiagnostic(run, diagnostic)
            }}
            className={cn(
              'inline-flex max-w-full items-center gap-1 rounded px-1 py-0.5 text-left text-xs leading-snug hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring',
              diagnostic.kind === 'tool-error' ? 'bg-red-500/10 text-red-600' : 'bg-amber-500/10 text-amber-700 dark:text-amber-400',
            )}
          >
            <AlertTriangle className="size-3 shrink-0" />
            <span>{diagnostic.label}</span>
          </button>
        ))}
      </div>
    ),
    cellClassName: 'whitespace-normal',
    headClassName: 'w-[185px]',
  },
]

interface DiagnosticFocus {
  runId: string
  diagnostic: PipelineDiagnostic
  revision: number
}

function DiagnosticEvidence({ focusRevision, children }: {
  focusRevision?: number
  children: React.ReactNode
}) {
  const evidenceRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (focusRevision == null) return
    evidenceRef.current?.focus({ preventScroll: true })
    evidenceRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [focusRevision])
  return (
    <div
      ref={evidenceRef}
      tabIndex={focusRevision == null ? undefined : -1}
      className="min-w-0 outline-none"
    >
      {children}
    </div>
  )
}

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
            <pre className="mt-1 whitespace-pre-wrap break-words rounded border bg-background px-2 py-1.5 text-[11px] leading-snug text-muted-foreground">
              {f.payload || '<empty>'}
            </pre>
          </details>
        ))}
      </div>
    </div>
  )
}

type UsageEntry = { call: LlmCallUsage; index: number }

function groupUsage(usage: LlmCallUsage[]): { tier: string; entries: UsageEntry[] }[] {
  const groups: { tier: string; entries: UsageEntry[] }[] = []
  usage.forEach((call, index) => {
    const last = groups[groups.length - 1]
    if (last && last.tier === call.tier) last.entries.push({ call, index })
    else groups.push({ tier: call.tier, entries: [{ call, index }] })
  })
  return groups
}

function CallLinks({ runId, index, call }: { runId: string; index: number; call: LlmCallUsage }) {
  const hasAny = call.hasRequestPayload || call.hasResponsePayload
  if (!hasAny) return null
  return (
    <a
      href={`/view/payload/${encodeURIComponent(runId)}/${index}`}
      target="_blank"
      rel="noreferrer"
      className="text-sky-600 underline underline-offset-2 hover:text-sky-700"
    >
      view payloads
    </a>
  )
}

function AttemptLabels({ call }: { call: LlmCallUsage }) {
  const failed = call.status === 'FAILED'
  return (
    <>
      {call.iteration != null && (
        <span className="tabular-nums text-foreground/60">
          iteration {call.iteration}/{call.totalIterations ?? '?'}
        </span>
      )}
      <span className="tabular-nums text-foreground/60">
        attempt {call.attempt}/{call.maxAttempts}
      </span>
      <span
        className={cn(
          'rounded px-1 text-[10px] font-medium lowercase',
          failed ? 'bg-red-500/10 text-red-600' : 'bg-emerald-500/10 text-emerald-600',
        )}
        title={call.error ?? undefined}
      >
        {call.status}
      </span>
      {failed && call.error && (
        <span className="max-w-80 truncate text-red-600" title={call.error}>
          {call.error}
        </span>
      )}
    </>
  )
}

function prettyArgs(args: string): string {
  if (!args) return ''
  try {
    return JSON.stringify(JSON.parse(args), null, 2)
  } catch {
    return args
  }
}

function prettyJson(value: string): string {
  if (!value) return ''
  try {
    const parsed = JSON.parse(value)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return value
  }
}

interface ExpansionCommand {
  expanded: boolean
  revision: number
}

function ToolResult({ value, command }: { value: string; command: ExpansionCommand }) {
  const [expanded, setExpanded] = useState(command.expanded)
  useEffect(() => setExpanded(command.expanded), [command.expanded, command.revision])
  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setExpanded((value) => !value)}
        className="absolute right-1 top-1 rounded bg-background/80 p-0.5 text-muted-foreground hover:text-foreground"
        title={expanded ? 'Shrink result' : 'Expand result'}
        aria-label={expanded ? 'Shrink result' : 'Expand result'}
      >
        {expanded ? <Minimize2 className="size-3" /> : <Maximize2 className="size-3" />}
      </button>
      <pre
        className={cn(
          'whitespace-pre-wrap break-words rounded bg-muted/40 px-1.5 py-1 pr-6 text-[11px] leading-snug text-muted-foreground',
          expanded ? 'max-h-none overflow-visible' : 'max-h-28 overflow-auto',
        )}
      >
        {prettyJson(value)}
      </pre>
    </div>
  )
}

function ToolCallRow({ entry, responseCommand }: { entry: ToolCallEntry; responseCommand: ExpansionCommand }) {
  const failedAttempts = entry.attempts.filter((attempt) => attempt.error).length
  const recovered = !entry.error && failedAttempts > 0
  return (
    <div
      className={cn(
        'rounded border px-1.5 py-1',
        entry.error ? 'border-red-500/40 bg-red-500/5' : 'border-border bg-background',
      )}
    >
      <div className="flex items-center gap-1.5">
        <Wrench className="size-3 text-sky-600" />
        <span className="font-medium text-foreground/80">{entry.name}</span>
        {entry.error && (
          <span className="rounded bg-red-500/10 px-1 text-[10px] font-normal text-red-600">error</span>
        )}
        {recovered && (
          <span className="rounded bg-amber-500/10 px-1 text-[10px] font-normal text-amber-700">
            recovered after {failedAttempts} failure{failedAttempts === 1 ? '' : 's'}
          </span>
        )}
      </div>
      {entry.arguments && (
        <div className="mt-1">
          <div className="text-[10px] uppercase tracking-wide text-muted-foreground/70">args</div>
          <pre className="mt-0.5 whitespace-pre-wrap break-words rounded bg-muted/40 px-1.5 py-1 text-[11px] leading-snug text-foreground/80">
            {prettyArgs(entry.arguments)}
          </pre>
        </div>
      )}
      {entry.attempts.length > 1 && (
        <div className="mt-1">
          <div className="text-[10px] uppercase tracking-wide text-muted-foreground/70">
            {entry.attempts.length}/{entry.maxAttempts} attempts
          </div>
          <ol className="mt-0.5 flex flex-col gap-1">
            {entry.attempts.map((attempt) => (
              <li key={attempt.attempt}>
                <div className={cn('text-[10px]', attempt.error ? 'text-red-600' : 'text-emerald-600')}>
                  attempt {attempt.attempt}/{entry.maxAttempts} · {attempt.error ? 'failed' : 'succeeded'}
                </div>
                <ToolResult value={attempt.result} command={responseCommand} />
              </li>
            ))}
          </ol>
        </div>
      )}
      {entry.result && entry.attempts.length <= 1 && (
        <div className="mt-1">
          <div className="text-[10px] uppercase tracking-wide text-muted-foreground/70">result</div>
          <div className="mt-0.5">
            <ToolResult value={entry.result} command={responseCommand} />
          </div>
        </div>
      )}
    </div>
  )
}

function ToolCalls({ calls, callCommand, responseCommand, focusRevision }: {
  calls: ToolCallEntry[]
  callCommand: ExpansionCommand
  responseCommand: ExpansionCommand
  focusRevision?: number
}) {
  const [open, setOpen] = useState(callCommand.expanded)
  useEffect(() => setOpen(callCommand.expanded), [callCommand.expanded, callCommand.revision])
  useEffect(() => {
    if (focusRevision != null) setOpen(true)
  }, [focusRevision])
  if (calls.length === 0) return null
  const errorCount = calls.filter((c) => c.error).length
  const retryFailureCount = calls.reduce(
    (sum, call) => sum + call.attempts.filter((attempt) => attempt.error).length,
    0,
  )
  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-fit items-center gap-1 rounded bg-sky-500/10 px-1 py-0.5 text-[11px] text-sky-600 hover:bg-sky-500/20"
      >
        {open ? <ChevronDown className="size-3" /> : <ChevronRight className="size-3" />}
        <Wrench className="size-3" />
        {calls.length} tool call{calls.length === 1 ? '' : 's'}
        {errorCount > 0 && (
          <span className="rounded bg-red-500/10 px-1 text-[10px] text-red-600">
            {errorCount} error{errorCount === 1 ? '' : 's'}
          </span>
        )}
        {retryFailureCount > 0 && (
          <span className="rounded bg-amber-500/10 px-1 text-[10px] text-amber-700">
            {retryFailureCount} failed attempt{retryFailureCount === 1 ? '' : 's'}
          </span>
        )}
      </button>
      {open && (
        <div className="flex flex-col gap-1">
          {calls.map((c, i) => (
            <ToolCallRow key={i} entry={c} responseCommand={responseCommand} />
          ))}
        </div>
      )}
    </div>
  )
}

function UsageChip({ runId, entry, callCommand, responseCommand, focusRevision }: {
  runId: string
  entry: UsageEntry
  callCommand: ExpansionCommand
  responseCommand: ExpansionCommand
  focusRevision?: number
}) {
  const { call, index } = entry
  const hasToolCalls = call.toolCalls.length > 0
  const toolErrorCount = call.toolCalls.filter((c) => c.error).length
  const toolRetryFailureCount = call.toolCalls.reduce(
    (sum, toolCall) => sum + toolCall.attempts.filter((attempt) => attempt.error).length,
    0,
  )
  return (
    <div className="flex flex-col gap-1">
      <span className="inline-flex items-center gap-1.5 rounded border bg-background px-1.5 py-0.5 text-[11px] text-muted-foreground">
        <span className="font-medium text-foreground/80">{call.tier}</span>
        <span>{call.model}</span>
        <AttemptLabels call={call} />
        <TokenSplit prompt={call.promptTokens} completion={call.completionTokens} total={call.tokens} />
        {call.tools.length > 0 && (
          <span className="rounded bg-sky-500/10 px-1 text-sky-600">{call.tools.join(', ')}</span>
        )}
        {toolErrorCount > 0 && (
          <span className="rounded bg-red-500/10 px-1 text-[10px] text-red-600">
            {toolErrorCount} tool error{toolErrorCount === 1 ? '' : 's'}
          </span>
        )}
        {toolRetryFailureCount > 0 && (
          <span className="rounded bg-amber-500/10 px-1 text-[10px] text-amber-700">
            {toolRetryFailureCount} failed tool attempt{toolRetryFailureCount === 1 ? '' : 's'}
          </span>
        )}
        <CallLinks runId={runId} index={index} call={call} />
      </span>
      {hasToolCalls && (
        <ToolCalls calls={call.toolCalls} callCommand={callCommand} responseCommand={responseCommand} focusRevision={focusRevision} />
      )}
    </div>
  )
}

function UsageGroup({ runId, tier, entries, callCommand, responseCommand, focus }: {
  runId: string
  tier: string
  entries: UsageEntry[]
  callCommand: ExpansionCommand
  responseCommand: ExpansionCommand
  focus?: DiagnosticFocus
}) {
  const [open, setOpen] = useState(callCommand.expanded)
  useEffect(() => setOpen(callCommand.expanded), [callCommand.expanded, callCommand.revision])
  const focusRevision = focus && entries.some((entry) => focus.diagnostic.callIndexes.includes(entry.index)) ? focus.revision : undefined
  useEffect(() => {
    if (focusRevision != null) setOpen(true)
  }, [focusRevision])
  const tokens = entries.reduce((sum, e) => sum + e.call.tokens, 0)
  const promptTokens = entries.reduce((sum, e) => sum + e.call.promptTokens, 0)
  const completionTokens = entries.reduce((sum, e) => sum + e.call.completionTokens, 0)
  const models = Array.from(new Set(entries.map((e) => e.call.model)))
  const tools = Array.from(new Set(entries.flatMap((e) => e.call.tools)))
  const failedAttempts = entries.filter((entry) => entry.call.status === 'FAILED').length
  const toolErrorCount = entries.reduce(
    (sum, e) => sum + e.call.toolCalls.filter((c) => c.error).length,
    0,
  )
  const toolRetryFailureCount = entries.reduce(
    (sum, entry) =>
      sum +
      entry.call.toolCalls.reduce(
        (toolSum, toolCall) => toolSum + toolCall.attempts.filter((attempt) => attempt.error).length,
        0,
      ),
    0,
  )
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
          {tier} · {entries.length} request attempt{entries.length === 1 ? '' : 's'}
        </span>
        <span>{models.length === 1 ? models[0] : `${models.length} models`}</span>
        {failedAttempts > 0 && (
          <span className="rounded bg-red-500/10 px-1 text-red-600">
            {failedAttempts} failed
          </span>
        )}
        {toolErrorCount > 0 && (
          <span className="rounded bg-red-500/10 px-1 text-red-600">
            {toolErrorCount} tool error{toolErrorCount === 1 ? '' : 's'}
          </span>
        )}
        {toolRetryFailureCount > 0 && (
          <span className="rounded bg-amber-500/10 px-1 text-amber-700">
            {toolRetryFailureCount} failed tool attempt{toolRetryFailureCount === 1 ? '' : 's'}
          </span>
        )}
        <TokenSplit prompt={promptTokens} completion={completionTokens} total={tokens} prefix="Σ" />
        {tools.length > 0 && (
          <span className="rounded bg-sky-500/10 px-1 text-sky-600">{tools.join(', ')}</span>
        )}
      </button>
      {open && (
        <ol className="ml-4 flex flex-col gap-1.5 border-l pl-2">
          {entries.map((e, i) => (
            <li key={e.index} className="flex flex-col gap-1">
              <DiagnosticEvidence
                focusRevision={focus?.diagnostic.callIndexes[0] === e.index ? focus.revision : undefined}
              >
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="tabular-nums text-foreground/50">#{i + 1}</span>
                  {models.length > 1 && <span>{e.call.model}</span>}
                  <AttemptLabels call={e.call} />
                  <TokenSplit
                    prompt={e.call.promptTokens}
                    completion={e.call.completionTokens}
                    total={e.call.tokens}
                  />
                  {e.call.tools.length > 0 && (
                    <span className="rounded bg-sky-500/10 px-1 text-sky-600">{e.call.tools.join(', ')}</span>
                  )}
                  <CallLinks runId={runId} index={e.index} call={e.call} />
                </div>
                {e.call.toolCalls.length > 0 && (
                  <ToolCalls
                    calls={e.call.toolCalls}
                    callCommand={callCommand}
                    responseCommand={responseCommand}
                    focusRevision={focus?.diagnostic.callIndexes.includes(e.index) ? focus.revision : undefined}
                  />
                )}
              </DiagnosticEvidence>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

function PipelineDetail({ run, focus }: { run: PipelineRun; focus?: DiagnosticFocus }) {
  const [callCommand, setCallCommand] = useState<ExpansionCommand>({ expanded: false, revision: 0 })
  const [responseCommand, setResponseCommand] = useState<ExpansionCommand>({ expanded: false, revision: 0 })
  const totalPrompt = run.llmUsage.reduce((s, u) => s + u.promptTokens, 0)
  const totalCompletion = run.llmUsage.reduce((s, u) => s + u.completionTokens, 0)
  return (
    <div className="flex flex-col gap-3">
      {run.outcomeDetail && (
        <div className="text-xs text-muted-foreground">
          Outcome <span className="font-medium text-foreground/80">{run.outcome}</span> — {run.outcomeDetail}
        </div>
      )}
      {(run.configRevisionId || run.contextSources.length > 0) && (
        <div className="flex flex-col gap-1.5">
          {run.configRevisionId && (
            <div className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
              <Cpu className="size-3 text-sky-500/70" />
              <span className="font-medium text-foreground/70">config</span>
              <span className="font-mono tabular-nums" title={run.configRevisionId}>
                {run.configRevisionId.slice(0, 8)}
              </span>
            </div>
          )}
          {run.contextSources.length > 0 && (
            <details className="group text-[11px]">
              <summary className="flex cursor-pointer list-none items-center gap-1.5 text-muted-foreground marker:hidden hover:text-foreground">
                <ChevronRight className="size-3 transition-transform group-open:rotate-90" />
                <BookOpen className="size-3 text-emerald-500/70" />
                <span className="font-medium text-foreground/70">context</span>
                <span className="tabular-nums">{run.contextSources.length} source{run.contextSources.length === 1 ? '' : 's'}</span>
              </summary>
              <div className="ml-5 mt-1 rounded border bg-background/50">
                {run.contextSources.map((source) => (
                  <div
                    key={source}
                    className="truncate border-b border-border/40 px-2 py-1 font-mono text-[10px] leading-normal text-muted-foreground/80 last:border-b-0 hover:text-foreground"
                    title={source}
                  >
                    {source}
                  </div>
                ))}
              </div>
            </details>
          )}
        </div>
      )}
      {run.jsonParseFailures.length > 0 && (
        <DiagnosticEvidence focusRevision={focus?.diagnostic.kind === 'json' ? focus.revision : undefined}>
          <JsonFailures failures={run.jsonParseFailures} />
        </DiagnosticEvidence>
      )}
      {run.llmUsage.length > 0 && (
        <div className="flex flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-3 text-xs font-medium">
            <span>LLM usage</span>
            <TokenSplit
              prompt={totalPrompt}
              completion={totalCompletion}
              total={run.totalTokens}
              prefix="Σ"
            />
            <label className="flex items-center gap-1.5 font-normal text-muted-foreground">
              <Switch
                checked={callCommand.expanded}
                onCheckedChange={(expanded) =>
                  setCallCommand((command) => ({ expanded, revision: command.revision + 1 }))
                }
                aria-label="Expand all tool calls"
              />
              tool calls
            </label>
            <label className="flex items-center gap-1.5 font-normal text-muted-foreground">
              <Switch
                checked={responseCommand.expanded}
                onCheckedChange={(expanded) =>
                  setResponseCommand((command) => ({ expanded, revision: command.revision + 1 }))
                }
                aria-label="Expand all tool responses"
              />
              responses
            </label>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {groupUsage(run.llmUsage).map((group, gi) =>
              group.entries.length === 1 ? (
                <DiagnosticEvidence
                  key={gi}
                  focusRevision={focus?.diagnostic.callIndexes[0] === group.entries[0]!.index ? focus.revision : undefined}
                >
                  <UsageChip
                    runId={run.id}
                    entry={group.entries[0]!}
                    callCommand={callCommand}
                    responseCommand={responseCommand}
                    focusRevision={focus?.diagnostic.callIndexes.includes(group.entries[0]!.index) ? focus.revision : undefined}
                  />
                </DiagnosticEvidence>
              ) : (
                <UsageGroup
                  key={gi}
                  runId={run.id}
                  tier={group.tier}
                  entries={group.entries}
                  callCommand={callCommand}
                  responseCommand={responseCommand}
                  focus={focus}
                />
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
  initialPipelineId,
  onError,
}: {
  initialPipelineId?: string | null
  onError: (message: string) => void
}) {
  const [diagnosticFocus, setDiagnosticFocus] = useState<DiagnosticFocus>()
  return (
    <DataTab<PipelineRun>
      columns={pipelineColumns((run, diagnostic) => setDiagnosticFocus((previous) => ({
        runId: run.id,
        diagnostic,
        revision: (previous?.revision ?? 0) + 1,
      })))}
      tableClassName="min-w-[1000px]"
      rowKey={(r) => r.id}
      load={(page, size, q, field, direction) =>
        consoleApi.listPipelineRuns(page, size, q, field, direction)
      }
      locate={(r, size, direction) =>
        consoleApi.locatePipelineRun(r.id, size, direction).then((res) => res.page)
      }
      initialFocusId={initialPipelineId}
      locateById={(id, size, direction) =>
        consoleApi.locatePipelineRun(id, size, direction).then((res) => res.page)
      }
      expand={(r) => <PipelineDetail run={r} focus={diagnosticFocus?.runId === r.id ? diagnosticFocus : undefined} />}
      roomAccessor={(r) => r.roomTarget}
      emptyLabel="No pipeline runs yet."
      sortLabel="created time"
      onError={onError}
    />
  )
}
