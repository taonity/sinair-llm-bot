'use client'

import { useEffect, useState, useCallback } from 'react'
import { JsonViewer } from '@textea/json-viewer'
import type { Colorspace } from '@textea/json-viewer'
import { Skeleton } from '@/components/ui/skeleton'
import { AlertTriangle, ChevronLeft, Eye, EyeOff, Maximize2, Minimize2 } from 'lucide-react'

interface PayloadViewerProps {
  runId: string
  index: string
}

interface PayloadState {
  request: object | null
  response: object | null
  error: string | null
  loading: boolean
}

/** GitHub Dark (VS Code) Base16 theme — matches the editor chrome. */
const vscodeDark: Colorspace = {
  base00: '#0d1117',
  base01: '#161b22',
  base02: '#21262d',
  base03: '#8b949e',
  base04: '#6e7681',
  base05: '#c9d1d9',
  base06: '#e6edf3',
  base07: '#f0f6fc',
  base08: '#ff7b72',
  base09: '#d2a8ff',
  base0A: '#d29922',
  base0B: '#3fb950',
  base0C: '#39c5cf',
  base0D: '#58a6ff',
  base0E: '#bc8cff',
  base0F: '#a371f7',
}

/** GitHub Light Base16 theme. */
const vscodeLight: Colorspace = {
  base00: '#ffffff',
  base01: '#f6f8fa',
  base02: '#e1e4e8',
  base03: '#6e7681',
  base04: '#57606a',
  base05: '#24292f',
  base06: '#1f2328',
  base07: '#0d1117',
  base08: '#cf222e',
  base09: '#8250df',
  base0A: '#9a6700',
  base0B: '#1a7f37',
  base0C: '#1b7c83',
  base0D: '#0969da',
  base0E: '#8250df',
  base0F: '#bc4c00',
}

/** Fetches a raw payload JSON blob from the console API proxy. */
async function fetchPayload(path: string): Promise<object | null> {
  const res = await fetch(path, { credentials: 'include' })
  if (!res.ok) {
    if (res.status === 404) return null
    throw new Error(`Failed to fetch payload (${res.status})`)
  }
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return { __raw: text }
  }
}

function JsonViewSkeleton() {
  return (
    <div className="flex flex-col gap-2 p-4">
      <Skeleton className="h-4 w-24" />
      <Skeleton className="h-3 w-full" />
      <Skeleton className="h-3 w-3/4" />
      <Skeleton className="h-3 w-1/2" />
      <Skeleton className="h-3 w-5/6" />
      <Skeleton className="h-3 w-2/3" />
    </div>
  )
}

export default function PayloadViewer({ runId, index }: PayloadViewerProps) {
  const [state, setState] = useState<PayloadState>({
    request: null,
    response: null,
    error: null,
    loading: true,
  })
  const [fullscreen, setFullscreen] = useState(false)
  const [showReq, setShowReq] = useState(true)
  const [showRes, setShowRes] = useState(true)
  const [isDark, setIsDark] = useState(false)

  useEffect(() => {
    setIsDark(document.documentElement.classList.contains('dark'))
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const base = `/api/console/pipeline-runs/${encodeURIComponent(runId)}/llm-usage/${encodeURIComponent(index)}`
        const [reqData, resData] = await Promise.all([
          fetchPayload(`${base}/request`),
          fetchPayload(`${base}/response`),
        ])
        if (cancelled) return
        setState({ request: reqData, response: resData, error: null, loading: false })
      } catch (err) {
        if (cancelled) return
        setState({
          request: null,
          response: null,
          error: err instanceof Error ? err.message : 'Unknown error',
          loading: false,
        })
      }
    }
    load()
    return () => { cancelled = true }
  }, [runId, index])

  const toggleFullscreen = useCallback(() => {
    setFullscreen((v) => !v)
  }, [])

  const theme = isDark ? vscodeDark : vscodeLight
  const hasRequest = state.request !== null
  const hasResponse = state.response !== null
  const oneHidden = !showReq || !showRes

  return (
    <div className={fullscreen ? 'fixed inset-0 z-50 flex flex-col bg-[#0d1117]' : 'fixed inset-0 flex flex-col bg-[#0d1117]'}>
      {/* Header bar — slim, VS Code chrome style */}
      <header className="flex shrink-0 items-center gap-3 border-b border-[#30363d] bg-[#161b22] px-3 py-1.5 text-[13px]">
        <a
          href="/"
          className="flex items-center gap-1 text-[#8b949e] hover:text-[#c9d1d9]"
        >
          <ChevronLeft className="size-3.5" />
          Back
        </a>
        <span className="h-3 w-px bg-[#30363d]" />
        <span className="font-medium text-[#c9d1d9]">LLM Payloads</span>
        <span className="text-[#8b949e]">run {runId.slice(0, 8)}… / #{index}</span>
        <div className="ml-auto flex items-center gap-1">
          <button
            type="button"
            onClick={() => setShowReq((v) => !v)}
            className="flex items-center gap-1 rounded px-1.5 py-0.5 text-xs text-[#8b949e] hover:bg-[#21262d] hover:text-[#c9d1d9]"
            title={showReq ? 'Hide request' : 'Show request'}
          >
            {showReq ? <Eye className="size-3" /> : <EyeOff className="size-3" />}
            Req
          </button>
          <button
            type="button"
            onClick={() => setShowRes((v) => !v)}
            className="flex items-center gap-1 rounded px-1.5 py-0.5 text-xs text-[#8b949e] hover:bg-[#21262d] hover:text-[#c9d1d9]"
            title={showRes ? 'Hide response' : 'Show response'}
          >
            {showRes ? <Eye className="size-3" /> : <EyeOff className="size-3" />}
            Res
          </button>
          <span className="h-3 w-px bg-[#30363d]" />
          <button
            type="button"
            onClick={toggleFullscreen}
            className="rounded px-1.5 py-0.5 text-[#8b949e] hover:bg-[#21262d] hover:text-[#c9d1d9]"
            title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}
          >
            {fullscreen ? <Minimize2 className="size-3.5" /> : <Maximize2 className="size-3.5" />}
          </button>
        </div>
      </header>

      {state.error && (
        <div className="flex items-center gap-2 border-b border-[#da3633]/40 bg-[#da3633]/10 px-3 py-1.5 text-sm text-[#ff7b72]">
          <AlertTriangle className="size-4" />
          {state.error}
        </div>
      )}

      {state.loading ? (
        <div className="grid flex-1 grid-cols-1 divide-x divide-[#30363d] overflow-hidden md:grid-cols-2">
          <JsonViewSkeleton />
          <JsonViewSkeleton />
        </div>
      ) : (
        <div
          className="grid flex-1 divide-x divide-[#30363d] overflow-hidden"
          style={{
            gridTemplateColumns:
              !showReq && !showRes
                ? '1fr 1fr'
                : showReq && showRes
                  ? '1fr 1fr'
                  : showReq
                    ? '1fr'
                    : '1fr',
          }}
        >
          {/* Request panel */}
          {showReq && (
            <div className="flex min-h-0 flex-col overflow-hidden">
              <div className="flex items-center gap-2 border-b border-[#30363d] bg-[#0d1117] px-3 py-1 text-[11px] font-medium uppercase tracking-wider text-[#8b949e]">
                <span className="size-2 rounded-full bg-[#58a6ff]" />
                Request
                {!hasRequest && <span className="text-[#6e7681]">— not available</span>}
              </div>
              <div className="peer flex-1 overflow-auto bg-[#0d1117] font-mono text-[13px] leading-[22px]">
                {hasRequest ? (
                  <div className="json-viewer-lines">
                    <JsonViewer
                      value={state.request!}
                      rootName={false}
                      defaultInspectDepth={8}
                      theme={theme}
                      enableClipboard={false}
                      displayDataTypes={false}
                      displaySize={false}
                      indentWidth={2}
                      collapseStringsAfterLength={999999}
                    />
                  </div>
                ) : (
                  <div className="p-3 italic text-[#6e7681]">No request data</div>
                )}
              </div>
            </div>
          )}

          {/* Response panel */}
          {showRes && (
            <div className="flex min-h-0 flex-col overflow-hidden">
              <div className="flex items-center gap-2 border-b border-[#30363d] bg-[#0d1117] px-3 py-1 text-[11px] font-medium uppercase tracking-wider text-[#8b949e]">
                <span className="size-2 rounded-full bg-[#3fb950]" />
                Response
                {!hasResponse && <span className="text-[#6e7681]">— not available</span>}
              </div>
              <div className="flex-1 overflow-auto bg-[#0d1117] font-mono text-[13px] leading-[22px]">
                {hasResponse ? (
                  <div className="json-viewer-lines">
                    <JsonViewer
                      value={state.response!}
                      rootName={false}
                      defaultInspectDepth={8}
                      theme={theme}
                      enableClipboard={false}
                      displayDataTypes={false}
                      displaySize={false}
                      indentWidth={2}
                      collapseStringsAfterLength={999999}
                    />
                  </div>
                ) : (
                  <div className="p-3 italic text-[#6e7681]">No response data</div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}