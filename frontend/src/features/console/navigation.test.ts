import { describe, expect, it } from 'vitest'
import { consoleLocationUrl, readConsoleLocation } from './navigation'

describe('console URL navigation', () => {
  it('opens a bookmarked tab and ignores a pipeline belonging to another tab', () => {
    expect(readConsoleLocation('?tab=events&pipeline=old-run')).toEqual({
      tab: 'events', pipelineId: null, outboundId: null,
    })
  })

  it('opens existing pipeline and outbound links without an explicit tab', () => {
    expect(readConsoleLocation('?pipeline=run%2Fwith+space')).toEqual({
      tab: 'pipelines', pipelineId: 'run/with space', outboundId: null,
    })
    expect(readConsoleLocation('?outbound=reply-id')).toEqual({
      tab: 'outbound', pipelineId: null, outboundId: 'reply-id',
    })
  })

  it('falls back safely for an unknown tab or an empty pipeline ID', () => {
    expect(readConsoleLocation('?tab=unknown&pipeline=')).toEqual({
      tab: 'messages', pipelineId: null, outboundId: null,
    })
  })

  it('preserves unrelated parameters and the anchor when selecting a pipeline', () => {
    const result = consoleLocationUrl('http://localhost/?source=review&outbound=old#console', {
      tab: 'pipelines', pipelineId: 'run/with space', outboundId: null,
    })
    expect(result).toBe('/?source=review&tab=pipelines&pipeline=run%2Fwith+space#console')
  })

  it('removes a selected pipeline when closing it or changing tabs', () => {
    expect(consoleLocationUrl('http://localhost/?tab=pipelines&pipeline=run-id', {
      tab: 'pipelines', pipelineId: null, outboundId: null,
    })).toBe('/?tab=pipelines')
    expect(consoleLocationUrl('http://localhost/?tab=pipelines&pipeline=run-id', {
      tab: 'messages', pipelineId: null, outboundId: null,
    })).toBe('/?tab=messages')
  })
})
