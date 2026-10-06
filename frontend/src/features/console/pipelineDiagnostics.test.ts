import { describe, expect, it } from 'vitest'
import { pipelineDiagnostics, pipelineOutcomeReason, responseDiagnostic } from './pipelineDiagnostics'
import type { BotResponseState, LlmCallUsage, PipelineRun, ToolCallEntry } from './types'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { ResponseDiagnostic } from './ResponseDiagnostic'
import { OutboundPipelineLink } from './DataConsole'

describe('outbound pipeline navigation', () => {
  it('links to the exact pipeline with an accessible label', () => {
    const html = renderToStaticMarkup(createElement(OutboundPipelineLink, { pipelineRunId: 'run/with space' }))
    expect(html).toContain('href="?pipeline=run%2Fwith%20space"')
    expect(html).toContain('aria-label="Open pipeline"')
    expect(html).toContain('title="Open pipeline"')
  })

  it('does not create a broken link when the trace is unavailable', () => {
    const html = renderToStaticMarkup(createElement(OutboundPipelineLink, { pipelineRunId: null }))
    expect(html).toContain('aria-label="Pipeline unavailable"')
    expect(html).not.toContain('href=')
  })
})

function response(overrides: Partial<BotResponseState> = {}): BotResponseState {
  return {
    status: 'DEFERRED', reason: 'WAITING_FOR_HUMANS', detail: 'An open question to the room.', category: 'open_question',
    deferredAt: '2026-10-06T12:00:00Z', nextAttemptAt: '2026-10-06T12:00:45Z', updatedAt: '2026-10-06T12:00:00Z',
    outboundMessageId: null, ...overrides,
  }
}

describe('responseDiagnostic', () => {
  it('distinguishes waiting, queued and delivered without losing delayed identity', () => {
    expect(responseDiagnostic(response())).toEqual({ label: 'Delayed: waiting', reason: 'Waiting for human replies' })
    expect(responseDiagnostic(response({ status: 'REPLY_QUEUED', reason: 'REPLY_GENERATED' }))?.label).toBe('Delayed: reply queued')
    expect(responseDiagnostic(response({ status: 'REPLIED', reason: 'DELIVERED' }))?.label).toBe('Delayed: replied')
  })

  it.each([
    ['RESTART', 'Backend restarted'], ['GATE_DECLINED', 'Gate declined'],
    ['AGENT_DECLINED', 'Reply agent declined'], ['NOT_ADDRESSED', 'Not addressed to the bot'],
  ])('identifies discard source %s', (reason, expected) => {
    expect(responseDiagnostic(response({ status: 'DISCARDED', reason }))).toEqual({ label: 'Delayed: discarded', reason: expected })
  })

  it('does not invent candidate history for legacy messages or direct replies', () => {
    expect(responseDiagnostic(null)).toBeNull()
    expect(responseDiagnostic(response({ status: 'REPLIED', deferredAt: null }))?.label).toBe('Replied')
  })

  it('renders the full discard reason and timing directly in expanded row content', () => {
    const html = renderToStaticMarkup(createElement(ResponseDiagnostic, { state: response({
      status: 'DISCARDED', reason: 'GATE_DECLINED', detail: 'Alice already answered the question in full.', nextAttemptAt: null,
    }) }))
    expect(html).toContain('aria-label="Message response"')
    expect(html).not.toContain('<details')
    expect(html).not.toContain('<summary')
    expect(html).toContain('Alice already answered the question in full.')
    expect(html).toContain('First deferred:')
    expect(html).not.toContain('Next check:')
  })

  it('links delivered replies to the outbound record', () => {
    const html = renderToStaticMarkup(createElement(ResponseDiagnostic, { state: response({ status: 'REPLIED', reason: 'DELIVERED', outboundMessageId: 'reply-id' }) }))
    expect(html).toContain('?outbound=reply-id')
    expect(html).toContain('View reply')
  })
})

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