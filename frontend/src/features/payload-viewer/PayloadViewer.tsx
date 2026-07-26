'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent, PointerEvent as ReactPointerEvent, ReactNode, SyntheticEvent } from 'react'
import { AlertTriangle, Check, ChevronLeft, Copy, Eye, EyeOff, Maximize2, Minimize2, WrapText } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'

interface PayloadViewerProps {
  runId: string
  index: string
}

interface Payload {
  formatted: string
}

interface PayloadState {
  request: Payload | null
  response: Payload | null
  error: string | null
  loading: boolean
}

/** Fetches and formats a raw payload while preserving non-JSON responses. */
async function fetchPayload(path: string): Promise<Payload | null> {
  const res = await fetch(path, { credentials: 'include' })
  if (!res.ok) {
    if (res.status === 404) return null
    throw new Error(`Failed to fetch payload (${res.status})`)
  }

  const text = await res.text()
  if (!text) return null
  try {
    return { formatted: JSON.stringify(JSON.parse(text), null, 2) }
  } catch {
    return { formatted: text }
  }
}

interface BracketPair {
  opening: number
  closing: number
}

const FULL_LINE_HIGHLIGHT_STYLE = {
  boxShadow: '0 -4px 0 rgb(38 79 120 / 0.45), 0 4px 0 rgb(38 79 120 / 0.45)',
}

/** Finds matching structural brackets while ignoring all brackets inside JSON strings. */
function findBracketPairs(json: string): BracketPair[] {
  const pairs: BracketPair[] = []
  const openings: Array<{ char: string, index: number }> = []
  const closingForOpening: Record<string, string> = { '{': '}', '[': ']', '(': ')' }
  let inString = false
  let escaped = false

  for (let index = 0; index < json.length; index += 1) {
    const char = json[index] ?? ''
    if (inString) {
      if (escaped) escaped = false
      else if (char === '\\') escaped = true
      else if (char === '"') inString = false
      continue
    }

    if (char === '"') inString = true
    else if (closingForOpening[char]) openings.push({ char, index })
    else if (char === '}' || char === ']' || char === ')') {
      const opening = openings.at(-1)
      if (opening && closingForOpening[opening.char] === char) {
        openings.pop()
        pairs.push({ opening: opening.index, closing: index })
      }
    }
  }

  return pairs
}

function findEnclosingPair(pairs: BracketPair[], cursor: number): BracketPair | null {
  return pairs
    .filter((pair) => pair.opening <= cursor && cursor <= pair.closing + 1)
    .sort((first, second) => (first.closing - first.opening) - (second.closing - second.opening))[0] ?? null
}

/** JSON is formatted with two spaces, so each indent level gets one editor guide. */
function indentationLevel(line: string): number {
  const indentation = line.match(/^\s*/)?.[0].length ?? 0
  return Math.floor(indentation / 2)
}

function bracketIndentationLevel(json: string, position: number): number {
  const lineStart = json.lastIndexOf('\n', position - 1) + 1
  return indentationLevel(json.slice(lineStart, position))
}

function wordAt(json: string, cursor: number): string | null {
  const isWordCharacter = (char: string | undefined) => Boolean(char && /[\p{L}\p{N}]/u.test(char))
  let start = cursor
  let end = cursor

  if (!isWordCharacter(json[start]) && isWordCharacter(json[start - 1])) start -= 1
  if (!isWordCharacter(json[start])) return null
  while (start > 0 && isWordCharacter(json[start - 1])) start -= 1
  while (end < json.length && isWordCharacter(json[end])) end += 1

  const word = json.slice(start, end)
  return word.length > 1 ? word : null
}

function highlightWordOccurrences(text: string, activeWord: string | null, keyPrefix: string): ReactNode[] {
  if (!activeWord) return [text]

  const escapedWord = activeWord.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const pattern = new RegExp(`(?<![\\p{L}\\p{N}])${escapedWord}(?![\\p{L}\\p{N}])`, 'giu')
  const nodes: ReactNode[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) nodes.push(text.slice(lastIndex, match.index))
    nodes.push(<mark className="bg-[#264f78]/45 text-inherit" key={`${keyPrefix}-${match.index}`} style={FULL_LINE_HIGHLIGHT_STYLE}>{match[0]}</mark>)
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < text.length) nodes.push(text.slice(lastIndex))
  return nodes.length ? nodes : [text]
}

/** Adds compact editor-style syntax colors without changing the copied payload. */
function highlightJsonLine(
  line: string,
  lineOffset: number,
  activePair: BracketPair | null,
  activeWord: string | null,
): ReactNode[] {
  const tokens: ReactNode[] = []
  const tokenPattern = /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/g
  let lastIndex = 0
  let match: RegExpExecArray | null

  const addPlainText = (text: string, startIndex: number) => {
    let position = startIndex
    for (const part of text.split(/([{}\[\]()])/)) {
      if (!part) continue
      if ('{}[]()'.includes(part)) {
        const isActive = position === activePair?.opening || position === activePair?.closing
        tokens.push(
          <span className={`text-[#e3b341] ${isActive ? 'bg-[#264f78]/45' : ''}`} key={`bracket-${position}`} style={isActive ? FULL_LINE_HIGHLIGHT_STYLE : undefined}>
            {part}
          </span>,
        )
      } else {
        tokens.push(...highlightWordOccurrences(part, activeWord, `plain-${position}`))
      }
      position += part.length
    }
  }

  while ((match = tokenPattern.exec(line)) !== null) {
    if (match.index > lastIndex) addPlainText(line.slice(lastIndex, match.index), lineOffset + lastIndex)

    const [token, stringValue, colon] = match
    const className = stringValue
      ? colon ? 'text-[#7ee787]' : 'text-[#a5d6ff]'
      : match[3] ? 'text-[#79c0ff]' : 'text-[#d2a8ff]'
    tokens.push(
      <span className={className} key={`${match.index}-${token}`}>
        {highlightWordOccurrences(token, activeWord, `token-${lineOffset + match.index}`)}
      </span>,
    )
    lastIndex = match.index + token.length
  }

  if (lastIndex < line.length) addPlainText(line.slice(lastIndex), lineOffset + lastIndex)
  return tokens
}

function JsonCodeViewer({ payload, wrapLines }: { payload: Payload, wrapLines: boolean }) {
  const [activePair, setActivePair] = useState<BracketPair | null>(null)
  const [activeLineStart, setActiveLineStart] = useState<number | null>(null)
  const [activeWord, setActiveWord] = useState<string | null>(null)
  const pairs = findBracketPairs(payload.formatted)
  const activeGuide = activePair ? bracketIndentationLevel(payload.formatted, activePair.opening) : null
  const lines = payload.formatted.split('\n')
  let lineOffset = 0

  const updateActivePair = useCallback(() => {
    const selection = window.getSelection()
    if (!selection?.rangeCount) return

    const range = selection.getRangeAt(0)
    const source = range.startContainer.nodeType === Node.ELEMENT_NODE
      ? range.startContainer as HTMLElement
      : range.startContainer.parentElement
    const line = source?.closest<HTMLElement>('[data-line-start]')
    if (!line) return

    const beforeCursor = document.createRange()
    beforeCursor.selectNodeContents(line)
    beforeCursor.setEnd(range.startContainer, range.startOffset)
    const lineStart = Number(line.dataset.lineStart)
    const cursor = lineStart + beforeCursor.toString().length
    setActiveLineStart(lineStart)
    setActivePair(findEnclosingPair(pairs, cursor))
    const selectedWord = selection.toString().trim()
    setActiveWord(/^[\p{L}\p{N}]{2,}$/u.test(selectedWord) ? selectedWord : wordAt(payload.formatted, cursor))
  }, [pairs, payload.formatted])

  const preventMutation = useCallback((event: SyntheticEvent) => event.preventDefault(), [])
  const preventEditingKeys = useCallback((event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key.length === 1 || ['Backspace', 'Delete', 'Enter'].includes(event.key)) event.preventDefault()
    if ((event.ctrlKey || event.metaKey) && ['v', 'x'].includes(event.key.toLowerCase())) event.preventDefault()
  }, [])

  return (
    <div
      aria-label="Read-only JSON payload"
      aria-multiline="true"
      className={`${wrapLines ? 'w-full min-w-0' : 'min-w-fit'} cursor-text py-2 pr-3 outline-none`}
      contentEditable
      onBeforeInput={preventMutation}
      onCut={preventMutation}
      onDrop={preventMutation}
      onKeyDown={preventEditingKeys}
      onKeyUp={updateActivePair}
      onMouseUp={updateActivePair}
      onPaste={preventMutation}
      onSelect={updateActivePair}
      role="textbox"
      spellCheck={false}
      suppressContentEditableWarning
    >
      {lines.map((line, index) => {
        const currentLineOffset = lineOffset
        const indentation = line.match(/^\s*/)?.[0].length ?? 0
        const guideCount = indentationLevel(line)
        lineOffset += line.length + 1
        return (
          <div className={`grid min-h-[22px] ${wrapLines ? 'grid-cols-[2.75rem_minmax(0,1fr)]' : 'grid-cols-[2.75rem_auto]'} ${activeLineStart === currentLineOffset ? 'bg-[#202224]' : ''}`} key={`${index}-${line}`}>
            <span className="select-none pr-2 text-right text-[#6e7681]" contentEditable={false}>{index + 1}</span>
            <code
              className={`relative block pl-3 text-[#c9d1d9] ${wrapLines ? 'whitespace-pre-wrap break-words' : 'whitespace-pre'}`}
              data-line-start={currentLineOffset}
              style={wrapLines ? { paddingLeft: `calc(0.75rem + ${indentation}ch)`, textIndent: `-${indentation}ch` } : undefined}
            >
              {Array.from({ length: guideCount }, (_, guide) => (
                <span
                  aria-hidden="true"
                  className={`pointer-events-none absolute inset-y-0 w-px ${
                    activePair && guide === activeGuide && currentLineOffset > activePair.opening && currentLineOffset <= activePair.closing
                      ? 'bg-[#8b949e]/80'
                      : 'bg-[#30363d]/75'
                  }`}
                  contentEditable={false}
                  key={`guide-${guide}`}
                  style={{ left: `calc(0.75rem + ${guide * 2}ch)` }}
                />
              ))}
              {highlightJsonLine(line, currentLineOffset, activePair, activeWord)}
            </code>
          </div>
        )
      })}
    </div>
  )
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

interface PayloadPanelProps {
  copied: boolean
  label: 'Request' | 'Response'
  onCopy: () => void
  payload: Payload | null
  wrapLines: boolean
}

function PayloadPanel({ copied, label, onCopy, payload, wrapLines }: PayloadPanelProps) {
  const isRequest = label === 'Request'

  return (
    <div className="flex min-h-0 flex-col overflow-hidden">
      <div className="flex items-center gap-2 border-b border-[#3f3f46] bg-[#27272a] px-3 py-1 text-[11px] font-medium uppercase tracking-wider text-[#a1a1aa]">
        <span className={`size-2 rounded-full ${isRequest ? 'bg-[#58a6ff]' : 'bg-[#3fb950]'}`} />
        <span>{label}</span>
        {!payload && <span className="text-[#6e7681]">— not available</span>}
        {payload && (
          <button
            type="button"
            onClick={onCopy}
            className="ml-auto flex items-center gap-1 rounded px-1.5 py-0.5 normal-case tracking-normal text-[#a1a1aa] hover:bg-[#3f3f46] hover:text-[#e4e4e7]"
            title={`Copy ${label.toLowerCase()} payload`}
          >
            {copied ? <Check className="size-3 text-[#3fb950]" /> : <Copy className="size-3" />}
            {copied ? 'Copied' : 'Copy'}
          </button>
        )}
      </div>
      <div className="flex-1 overflow-auto bg-[#18181b] font-mono text-[13px] leading-[22px]">
        {payload ? <JsonCodeViewer payload={payload} wrapLines={wrapLines} /> : <div className="p-3 italic text-[#6e7681]">No {label.toLowerCase()} data</div>}
      </div>
    </div>
  )
}

interface ResizeHandleProps {
  isResizing: boolean
  onKeyDown: (event: KeyboardEvent<HTMLDivElement>) => void
  onPointerDown: (event: ReactPointerEvent<HTMLDivElement>) => void
  onPointerMove: (event: ReactPointerEvent<HTMLDivElement>) => void
  onPointerUp: (event: ReactPointerEvent<HTMLDivElement>) => void
  splitPercent: number
}

function ResizeHandle({ isResizing, onKeyDown, onPointerDown, onPointerMove, onPointerUp, splitPercent }: ResizeHandleProps) {
  return (
    <div
      aria-label="Resize request and response panels"
      aria-orientation="vertical"
      aria-valuemax={80}
      aria-valuemin={20}
      aria-valuenow={Math.round(splitPercent)}
      className={`group relative cursor-col-resize touch-none outline-none ${isResizing ? 'bg-[#58a6ff]/20' : 'hover:bg-[#58a6ff]/10 focus-visible:bg-[#58a6ff]/10'}`}
      onKeyDown={onKeyDown}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      role="separator"
      tabIndex={0}
    >
      <span className={`pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 ${isResizing ? 'bg-[#58a6ff]' : 'bg-[#30363d] group-hover:bg-[#58a6ff] group-focus-visible:bg-[#58a6ff]'}`} />
    </div>
  )
}

export default function PayloadViewer({ runId, index }: PayloadViewerProps) {
  const [state, setState] = useState<PayloadState>({ request: null, response: null, error: null, loading: true })
  const [fullscreen, setFullscreen] = useState(false)
  const [showReq, setShowReq] = useState(true)
  const [showRes, setShowRes] = useState(true)
  const [copied, setCopied] = useState<'request' | 'response' | null>(null)
  const [wrapLines, setWrapLines] = useState(true)
  const [splitPercent, setSplitPercent] = useState(50)
  const [isResizing, setIsResizing] = useState(false)
  const panelsRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const base = `/api/console/pipeline-runs/${encodeURIComponent(runId)}/llm-usage/${encodeURIComponent(index)}`
        const [request, response] = await Promise.all([fetchPayload(`${base}/request`), fetchPayload(`${base}/response`)])
        if (!cancelled) setState({ request, response, error: null, loading: false })
      } catch (error) {
        if (!cancelled) setState({ request: null, response: null, error: error instanceof Error ? error.message : 'Unknown error', loading: false })
      }
    }
    load()
    return () => { cancelled = true }
  }, [runId, index])

  const copyPayload = useCallback(async (kind: 'request' | 'response', payload: Payload) => {
    try {
      await navigator.clipboard.writeText(payload.formatted)
      setCopied(kind)
      window.setTimeout(() => setCopied((current) => current === kind ? null : current), 1500)
    } catch {
      setState((current) => ({ ...current, error: 'Could not copy the payload to the clipboard.' }))
    }
  }, [])

  const updateSplit = useCallback((clientX: number) => {
    const bounds = panelsRef.current?.getBoundingClientRect()
    if (!bounds) return
    const nextPercent = ((clientX - bounds.left) / bounds.width) * 100
    setSplitPercent(Math.min(80, Math.max(20, nextPercent)))
  }, [])

  const handleResizeStart = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    setIsResizing(true)
    updateSplit(event.clientX)
  }, [updateSplit])

  const handleResizeMove = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    if (isResizing) updateSplit(event.clientX)
  }, [isResizing, updateSplit])

  const handleResizeEnd = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
    setIsResizing(false)
  }, [])

  const handleResizeKeyDown = useCallback((event: KeyboardEvent<HTMLDivElement>) => {
    const change = event.key === 'ArrowLeft' ? -2 : event.key === 'ArrowRight' ? 2 : 0
    if (change) {
      event.preventDefault()
      setSplitPercent((value) => Math.min(80, Math.max(20, value + change)))
    }
  }, [])

  const bothPanelsVisible = showReq && showRes

  return (
    <div className={fullscreen ? 'fixed inset-0 z-50 flex flex-col bg-[#18181b]' : 'fixed inset-0 flex flex-col bg-[#18181b]'}>
      <header className="flex shrink-0 items-center gap-3 border-b border-[#3f3f46] bg-[#27272a] px-3 py-1.5 text-[13px]">
        <a href="/" className="flex items-center gap-1 text-[#8b949e] hover:text-[#c9d1d9]"><ChevronLeft className="size-3.5" />Back</a>
        <span className="h-3 w-px bg-[#30363d]" />
        <span className="font-medium text-[#c9d1d9]">LLM Payloads</span>
        <span className="text-[#8b949e]">run {runId.slice(0, 8)}… / #{index}</span>
        <div className="ml-auto flex items-center gap-1">
          <button type="button" onClick={() => setShowReq((visible) => !visible)} className="flex items-center gap-1 rounded px-1.5 py-0.5 text-xs text-[#a1a1aa] hover:bg-[#3f3f46] hover:text-[#e4e4e7]" title={showReq ? 'Hide request' : 'Show request'}>{showReq ? <Eye className="size-3" /> : <EyeOff className="size-3" />}Req</button>
          <button type="button" onClick={() => setShowRes((visible) => !visible)} className="flex items-center gap-1 rounded px-1.5 py-0.5 text-xs text-[#a1a1aa] hover:bg-[#3f3f46] hover:text-[#e4e4e7]" title={showRes ? 'Hide response' : 'Show response'}>{showRes ? <Eye className="size-3" /> : <EyeOff className="size-3" />}Res</button>
          <button type="button" onClick={() => setWrapLines((value) => !value)} className={`flex items-center gap-1 rounded px-1.5 py-0.5 text-xs hover:bg-[#3f3f46] ${wrapLines ? 'text-[#e4e4e7]' : 'text-[#a1a1aa]'}`} title={wrapLines ? 'Disable line wrapping' : 'Wrap long lines'}><WrapText className="size-3" />Wrap</button>
          <span className="h-3 w-px bg-[#30363d]" />
          <button type="button" onClick={() => setFullscreen((value) => !value)} className="rounded px-1.5 py-0.5 text-[#a1a1aa] hover:bg-[#3f3f46] hover:text-[#e4e4e7]" title={fullscreen ? 'Exit fullscreen' : 'Fullscreen'}>{fullscreen ? <Minimize2 className="size-3.5" /> : <Maximize2 className="size-3.5" />}</button>
        </div>
      </header>

      {state.error && <div className="flex items-center gap-2 border-b border-[#da3633]/40 bg-[#da3633]/10 px-3 py-1.5 text-sm text-[#ff7b72]"><AlertTriangle className="size-4" />{state.error}</div>}

      {state.loading ? (
        <div className="grid flex-1 grid-cols-1 overflow-hidden md:grid-cols-2"><JsonViewSkeleton /><JsonViewSkeleton /></div>
      ) : (
        <div className="grid flex-1 overflow-hidden" ref={panelsRef} style={{ gridTemplateColumns: bothPanelsVisible ? `${splitPercent}fr 6px ${100 - splitPercent}fr` : '1fr' }}>
          {showReq && <PayloadPanel label="Request" payload={state.request} copied={copied === 'request'} onCopy={() => state.request && copyPayload('request', state.request)} wrapLines={wrapLines} />}
          {bothPanelsVisible && <ResizeHandle isResizing={isResizing} onKeyDown={handleResizeKeyDown} onPointerDown={handleResizeStart} onPointerMove={handleResizeMove} onPointerUp={handleResizeEnd} splitPercent={splitPercent} />}
          {showRes && <PayloadPanel label="Response" payload={state.response} copied={copied === 'response'} onCopy={() => state.response && copyPayload('response', state.response)} wrapLines={wrapLines} />}
        </div>
      )}
    </div>
  )
}
