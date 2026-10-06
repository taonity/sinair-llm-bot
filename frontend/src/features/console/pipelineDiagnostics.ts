import type { BotResponseState, PipelineRun } from './types'

const RESPONSE_STATUSES: Record<string, string> = {
  PENDING: 'Awaiting assessment',
  DEFERRED: 'Waiting',
  DISCARDED: 'Discarded',
  REPLY_QUEUED: 'Reply queued',
  REPLIED: 'Replied',
  FAILED: 'Failed',
}

const RESPONSE_REASONS: Record<string, string> = {
  ASSESSMENT_PENDING: 'Assessment pending',
  WAITING_FOR_HUMANS: 'Waiting for human replies',
  COOLDOWN: 'Cooldown',
  ASSESSMENT_FAILED: 'Assessment failed; retry pending',
  RESTART: 'Backend restarted',
  GATE_DECLINED: 'Gate declined',
  NOT_ADDRESSED: 'Not addressed to the bot',
  AGENT_DECLINED: 'Reply agent declined',
  SUPERSEDED: 'Resolved or superseded during generation',
  MUTED: 'Room muted',
  COMMAND: 'Command handled',
  REPLY_GENERATED: 'Awaiting delivery',
  DELIVERED: 'Collector acknowledged delivery',
  GENERATION_FAILED: 'Generation failed; error notice queued',
}

export function responseDiagnostic(state?: BotResponseState | null) {
  if (!state) return null
  const status = RESPONSE_STATUSES[state.status] ?? state.status
  return {
    label: state.deferredAt ? `Delayed: ${status.toLowerCase()}` : status,
    reason: RESPONSE_REASONS[state.reason] ?? state.reason,
  }
}

export type DiagnosticKind = 'llm' | 'tool-error' | 'tool-recovered' | 'json'

export interface PipelineDiagnostic {
  kind: DiagnosticKind
  label: string
  count: number
  callIndexes: number[]
}

export function pipelineDiagnostics(run: PipelineRun): PipelineDiagnostic[] {
  const failedCalls: number[] = []
  const toolErrors: number[] = []
  const recoveredTools: number[] = []
  run.llmUsage.forEach((call, index) => {
    if (call.status === 'FAILED') failedCalls.push(index)
    call.toolCalls.forEach((tool) => {
      if (tool.error) toolErrors.push(index)
      else if (tool.attempts.some((attempt) => attempt.error)) recoveredTools.push(index)
    })
  })

  const diagnostics: PipelineDiagnostic[] = []
  if (failedCalls.length > 0) {
    diagnostics.push({
      kind: 'llm',
      label: `${failedCalls.length} failed LLM attempt${failedCalls.length === 1 ? '' : 's'}`,
      count: failedCalls.length,
      callIndexes: failedCalls,
    })
  }
  if (toolErrors.length > 0) {
    diagnostics.push({
      kind: 'tool-error',
      label: `${toolErrors.length} tool error${toolErrors.length === 1 ? '' : 's'}`,
      count: toolErrors.length,
      callIndexes: [...new Set(toolErrors)],
    })
  }
  if (recoveredTools.length > 0) {
    diagnostics.push({
      kind: 'tool-recovered',
      label: `${recoveredTools.length} tool${recoveredTools.length === 1 ? '' : 's'} recovered`,
      count: recoveredTools.length,
      callIndexes: [...new Set(recoveredTools)],
    })
  }
  if (run.jsonParseFailures.length > 0) {
    diagnostics.push({
      kind: 'json',
      label: `${run.jsonParseFailures.length} JSON failure${run.jsonParseFailures.length === 1 ? '' : 's'}`,
      count: run.jsonParseFailures.length,
      callIndexes: [],
    })
  }
  return diagnostics
}

export function pipelineOutcomeReason(run: PipelineRun): string | null {
  if (run.outcomeDetail === 'driver=none') return 'Triage chose silence'
  return run.outcomeDetail?.trim() || null
}