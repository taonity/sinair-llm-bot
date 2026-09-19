import { describe, expect, it } from 'vitest'
import { pipelineDiagnostics, pipelineOutcomeReason } from './pipelineDiagnostics'
import type { LlmCallUsage, PipelineRun, ToolCallEntry } from './types'

function call(overrides: Partial<LlmCallUsage> = {}): LlmCallUsage {
  return {
    tier: 'reply', model: 'model', tokens: 10, tools: [], toolCalls: [],
    hasRequestPayload: false, hasResponsePayload: false, promptTokens: 5, completionTokens: 5,
    attempt: 1, maxAttempts: 3, status: 'SUCCEEDED', error: null,
    iteration: null, totalIterations: null, ...overrides,
  }
}

function tool(overrides: Partial<ToolCallEntry> = {}): ToolCallEntry {
  return { name: 'search', arguments: '{}', result: 'ok', error: false, attempts: [], maxAttempts: 3, ...overrides }
}

function run(overrides: Partial<PipelineRun> = {}): PipelineRun {
  return {
    id: 'run', pipelineKey: 'reply', roomTarget: 'room', triggerMessageId: null,
    triggerSenderLogin: 'user', triggerText: 'hello', outcome: 'REPLIED', outcomeDetail: null,
    outboundMessageId: null, stages: [], totalTokens: 0, llmUsage: [], jsonParseFailures: [],
    configRevisionId: null, contextSources: [], createdAt: '2026-09-19T00:00:00Z', ...overrides,
  }
}

describe('pipelineDiagnostics', () => {
  it('keeps ordinary runs and successful agent iterations quiet', () => {
    expect(pipelineDiagnostics(run())).toEqual([])
    expect(pipelineDiagnostics(run({
      llmUsage: [1, 2, 3].map((iteration) => call({ iteration, totalIterations: 3, toolCalls: [tool()] })),
    }))).toEqual([])
  })

  it('counts failed LLM attempts without assuming a successful outcome means recovery', () => {
    const diagnostics = pipelineDiagnostics(run({
      llmUsage: [call({ status: 'FAILED' }), call({ status: 'FAILED', attempt: 2 }), call({ attempt: 3 })],
    }))
    expect(diagnostics).toEqual([{ kind: 'llm', count: 2, label: '2 failed LLM attempts', callIndexes: [0, 1] }])
  })

  it('separates final tool errors from recovered tools and counts tools, not retry attempts', () => {
    const attempts = [
      { attempt: 1, result: 'timeout', error: true },
      { attempt: 2, result: 'timeout', error: true },
    ]
    const diagnostics = pipelineDiagnostics(run({
      llmUsage: [call({ toolCalls: [
        tool({ error: true, attempts }),
        tool({ attempts: [...attempts, { attempt: 3, result: 'ok', error: false }] }),
        tool({ attempts: [...attempts, { attempt: 3, result: 'ok', error: false }] }),
      ] })],
    }))
    expect(diagnostics).toEqual([
      { kind: 'tool-error', count: 1, label: '1 tool error', callIndexes: [0] },
      { kind: 'tool-recovered', count: 2, label: '2 tools recovered', callIndexes: [0] },
    ])
  })

  it('retains final errors for legacy tools without attempt history', () => {
    expect(pipelineDiagnostics(run({ llmUsage: [call({ toolCalls: [tool({ error: true })] })] })))
      .toEqual([{ kind: 'tool-error', count: 1, label: '1 tool error', callIndexes: [0] }])
  })

  it('counts JSON failures independently', () => {
    expect(pipelineDiagnostics(run({ jsonParseFailures: [{ label: 'triage', attempt: 1, payload: 'bad' }] })))
      .toEqual([{ kind: 'json', count: 1, label: '1 JSON failure', callIndexes: [] }])
  })
})

describe('pipelineOutcomeReason', () => {
  it('makes the triage silence reason readable', () => {
    expect(pipelineOutcomeReason(run({ outcome: 'SILENT', outcomeDetail: 'driver=none' })))
      .toBe('Triage chose silence')
  })

  it('preserves distinct silent and failure reasons', () => {
    for (const outcomeDetail of ['deferred contribution', 'superseded during generation', 'no remaining contribution', 'assessment failed; request retained']) {
      expect(pipelineOutcomeReason(run({ outcomeDetail }))).toBe(outcomeDetail)
    }
  })

  it('does not invent reasons for traces without details', () => {
    expect(pipelineOutcomeReason(run())).toBeNull()
    expect(pipelineOutcomeReason(run({ outcomeDetail: ' ' }))).toBeNull()
  })
})