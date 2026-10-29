const TABS = ['messages', 'events', 'outbound', 'pipelines', 'summaries', 'config', 'admin', 'about'] as const

export type TabKey = typeof TABS[number]

export type ConsoleLocation = {
  tab: TabKey
  pipelineId: string | null
  outboundId: string | null
}

export function readConsoleLocation(search: string): ConsoleLocation {
  const params = new URLSearchParams(search)
  const requestedTab = params.get('tab')
  const pipelineId = params.get('pipeline') || null
  const outboundId = params.get('outbound') || null
  const tab = TABS.find((value) => value === requestedTab)
    ?? (pipelineId ? 'pipelines' : outboundId ? 'outbound' : 'messages')

  return {
    tab,
    pipelineId: tab === 'pipelines' ? pipelineId : null,
    outboundId: tab === 'outbound' ? outboundId : null,
  }
}

export function consoleLocationUrl(currentUrl: string, location: ConsoleLocation): string {
  const url = new URL(currentUrl)
  url.searchParams.set('tab', location.tab)
  url.searchParams.delete('pipeline')
  url.searchParams.delete('outbound')
  if (location.tab === 'pipelines' && location.pipelineId) url.searchParams.set('pipeline', location.pipelineId)
  if (location.tab === 'outbound' && location.outboundId) url.searchParams.set('outbound', location.outboundId)
  return `${url.pathname}${url.search}${url.hash}`
}
