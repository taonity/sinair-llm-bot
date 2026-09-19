import type { PipelineRun } from './types'

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