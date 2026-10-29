'use client'

import { Fragment, useCallback, useEffect, useRef, useState } from 'react'
import {
  ArrowDownWideNarrow,
  ArrowRightToLine,
  ArrowUpWideNarrow,
  ChevronDown,
  ChevronRight,
  RotateCw,
  Trash2,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { PageResponse } from './types'
import { DataPagination } from './DataPagination'

export type Column<T> = {
  key: string
  label: string
  value: (row: T) => string
  render?: (row: T, actions: { expand: () => void }) => React.ReactNode
  cellClassName?: string
  headClassName?: string
  skeleton?: string
  searchKey?: string
  mobileContent?: boolean
}

type DataTabProps<T> = {
  columns: Column<T>[]
  rowKey: (row: T) => string
  load: (page: number, size: number, q?: string, field?: string, direction?: string) => Promise<PageResponse<T>>
  locate?: (row: T, size: number, direction?: string) => Promise<number>
  initialFocusId?: string | null
  locateById?: (id: string, size: number, direction?: string) => Promise<number>
  expand?: (row: T) => React.ReactNode
  expandOnRowClick?: boolean
  roomAccessor?: (row: T) => string
  canEdit?: boolean
  rowActions?: (row: T) => React.ReactNode
  onDelete?: (row: T) => Promise<unknown>
  emptyLabel: string
  sortLabel?: string
  tableClassName?: string
  onError: (message: string) => void
}

const DEFAULT_PAGE_SIZE = 50
const SKELETON_ROWS = 10

const SKELETON_BAR_WIDTHS = [
  'w-[85%]',
  'w-[58%]',
  'w-[72%]',
  'w-[46%]',
  'w-[90%]',
  'w-[64%]',
  'w-[78%]',
  'w-[52%]',
]

export function DataTab<T>({
  columns,
  rowKey,
  load,
  locate,
  initialFocusId,
  locateById,
  expand,
  expandOnRowClick = false,
  roomAccessor,
  canEdit = false,
  rowActions,
  onDelete,
  emptyLabel,
  sortLabel = 'time',
  tableClassName,
  onError,
}: DataTabProps<T>) {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE)
  const [data, setData] = useState<PageResponse<T> | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [query, setQuery] = useState('')
  const [activeQuery, setActiveQuery] = useState('')
  const [field, setField] = useState('all')
  const [direction, setDirection] = useState<'desc' | 'asc'>('desc')
  const [highlightId, setHighlightId] = useState<string | null>(null)
  const [jumpingId, setJumpingId] = useState<string | null>(null)
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const topPaginationRef = useRef<HTMLElement>(null)
  const highlightTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const rowRefs = useRef(new Map<string, HTMLTableRowElement>())

  const reload = useCallback(
    async (
      targetPage: number,
      targetSize: number,
      q: string,
      searchField: string,
      dir: string,
      opts?: { silent?: boolean },
    ) => {
      if (opts?.silent) setRefreshing(true)
      else setLoading(true)
      try {
        const result = await load(targetPage, targetSize, q.trim() || undefined, searchField, dir)
        setData(result)
        setPage(result.page)
        setSize(result.size)
      } catch {
        onError('Failed to load data.')
      } finally {
        if (opts?.silent) setRefreshing(false)
        else setLoading(false)
      }
    },
    [load, onError],
  )

  const didInitialLoad = useRef(false)
  useEffect(() => {
    if (didInitialLoad.current) return
    didInitialLoad.current = true
    if (!initialFocusId || !locateById) {
      void reload(0, DEFAULT_PAGE_SIZE, '', 'all', 'desc')
      return
    }

    const focusInitialRow = async () => {
      try {
        const targetPage = await locateById(initialFocusId, DEFAULT_PAGE_SIZE, 'desc')
        await reload(targetPage, DEFAULT_PAGE_SIZE, '', 'all', 'desc')
        setExpandedIds(new Set([initialFocusId]))
        setHighlightId(initialFocusId)
        highlightTimer.current = setTimeout(() => setHighlightId(null), 3000)
      } catch {
        onError('Failed to locate the linked pipeline run.')
        await reload(0, DEFAULT_PAGE_SIZE, '', 'all', 'desc')
      }
    }
    void focusInitialRow()
  }, [initialFocusId, locateById, onError, reload])

  useEffect(() => {
    if (!highlightId) return
    rowRefs.current.get(highlightId)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [data, highlightId])

  useEffect(
    () => () => {
      if (highlightTimer.current) clearTimeout(highlightTimer.current)
      if (searchTimer.current) clearTimeout(searchTimer.current)
    },
    [],
  )

  const onSearchChange = (value: string) => {
    setQuery(value)
    if (searchTimer.current) clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => {
      setActiveQuery(value)
      void reload(0, size, value, field, direction)
    }, 300)
  }

  const onFieldChange = (next: string) => {
    setField(next)
    if (activeQuery.trim()) {
      void reload(0, size, activeQuery, next, direction)
    }
  }

  const returnToPagination = () => {
    topPaginationRef.current?.scrollIntoView({ block: 'start', behavior: 'instant' })
    topPaginationRef.current?.focus({ preventScroll: true })
  }

  const onSizeChange = async (next: number, fromBottom = false) => {
    await reload(0, next, activeQuery, field, direction)
    if (fromBottom) returnToPagination()
  }

  const onPageChange = async (next: number, fromBottom = false) => {
    await reload(next, size, activeQuery, field, direction)
    if (fromBottom) returnToPagination()
  }

  const onToggleDirection = () => {
    const next = direction === 'desc' ? 'asc' : 'desc'
    setDirection(next)
    void reload(0, size, activeQuery, field, next)
  }

  const searching = activeQuery.trim().length > 0
  const rows = data?.content ?? []
  const hasActions = Boolean(rowActions || canEdit)
  const columnCount = columns.length + (expand ? 1 : 0) + (locate ? 1 : 0) + (hasActions ? 1 : 0)
  const firstRow = rows[0]
  const roomName = roomAccessor && firstRow ? roomAccessor(firstRow) : null
  const searchableColumns = columns.filter((c) => c.searchKey)
  const initialLoading = loading && data === null

  const toggleExpanded = (id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const remove = async (row: T) => {
    if (!onDelete) return
    if (!confirm('Delete this row? This action cannot be undone.')) return
    try {
      await onDelete(row)
      await reload(page, size, activeQuery, field, direction)
    } catch {
      onError('Failed to delete row.')
    }
  }

  const jumpTo = async (row: T) => {
    if (!locate) return
    const id = rowKey(row)
    setJumpingId(id)
    try {
      const targetPage = await locate(row, size, direction)
      if (searchTimer.current) clearTimeout(searchTimer.current)
      setQuery('')
      setActiveQuery('')
      await reload(targetPage, size, '', field, direction)
      setHighlightId(id)
      if (highlightTimer.current) clearTimeout(highlightTimer.current)
      highlightTimer.current = setTimeout(() => setHighlightId(null), 3000)
    } catch {
      onError('Failed to locate the selected row.')
    } finally {
      setJumpingId(null)
    }
  }

  const paginationProps = {
    page,
    size,
    rowCount: rows.length,
    totalElements: data?.totalElements,
    totalPages: data?.totalPages,
    loading,
  }

  return (
    <div className="data-tab flex min-w-0 flex-col gap-2 sm:gap-3">
      <div className="data-toolbar flex flex-wrap items-center gap-2 sm:justify-between">
        <div className="data-search flex min-w-0 flex-1 flex-wrap items-center gap-2">
          {roomAccessor && (roomName || initialLoading) &&
            (roomName ? (
              <Badge variant="outline" className="room-badge h-6 max-w-full font-normal whitespace-normal wrap-anywhere">
                Room: {roomName}
              </Badge>
            ) : (
              <Badge variant="outline" className="room-badge h-6 font-normal">
                <Skeleton className="h-3 w-20" />
              </Badge>
            ))}
          {searchableColumns.length > 0 && (
            <Select
              items={{
                all: 'All columns',
                ...Object.fromEntries(searchableColumns.map((c) => [c.searchKey!, c.label])),
              }}
              value={field}
              onValueChange={(v) => v && onFieldChange(v)}
            >
              <SelectTrigger size="sm" className="search-field h-7 w-[130px]" aria-label="Search column">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All columns</SelectItem>
                {searchableColumns.map((c) => (
                  <SelectItem key={c.searchKey} value={c.searchKey!}>
                    {c.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          <Input
            className="search-input h-7 min-w-0 flex-1 sm:w-56 sm:flex-none"
            aria-label="Search all rows"
            placeholder="Search all rows…"
            value={query}
            onChange={(e) => onSearchChange(e.target.value)}
          />
          {searching && data && (
            <span className="text-xs text-muted-foreground">
              {data.totalElements} match{data.totalElements === 1 ? '' : 'es'} across all rows
            </span>
          )}
        </div>
        <div className="data-refresh flex items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            disabled={loading}
            onClick={onToggleDirection}
            title={`Sort by ${sortLabel}: ${direction === 'desc' ? 'newest first' : 'oldest first'}`}
          >
            {direction === 'desc' ? <ArrowDownWideNarrow /> : <ArrowUpWideNarrow />}
            {direction === 'desc' ? 'Newest' : 'Oldest'}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            disabled={loading || refreshing}
            onClick={() => reload(page, size, activeQuery, field, direction, { silent: true })}
          >
            <RotateCw className={loading || refreshing ? 'animate-spin' : ''} />
            Refresh
          </Button>
        </div>
      </div>

      <DataPagination
        {...paginationProps}
        position="top"
        paginationRef={topPaginationRef}
        onPageChange={(next) => void onPageChange(next)}
        onSizeChange={(next) => void onSizeChange(next)}
      />

      <div className="data-table-frame min-w-0 overflow-hidden rounded-lg border" aria-busy={loading || refreshing}>
        <Table className={cn('mobile-records min-w-[720px] table-fixed [&_td]:py-1.5 [&_th]:h-9 [&_tr]:border-border/50', tableClassName)}>
          <TableHeader>
            <TableRow className="bg-muted/40">
              {expand && <TableHead className="w-[40px]" />}
              {columns.map((c) => (
                <TableHead key={c.key} className={c.headClassName}>
                  {c.label}
                </TableHead>
              ))}
              {locate && <TableHead className="w-[48px]" />}
              {hasActions && <TableHead className="w-[96px] pr-4 text-right">Actions</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {initialLoading &&
              Array.from({ length: SKELETON_ROWS }).map((_, i) => (
                <TableRow key={`skeleton-${i}`} className="record-row hover:bg-transparent" aria-hidden="true">
                  {expand && <TableCell className="record-toggle w-[40px]" />}
                  {columns.map((c, idx) => (
                    <TableCell key={c.key} data-column={c.key} data-label={c.label} data-content={c.mobileContent || undefined} className={c.cellClassName}>
                      <Skeleton
                        className={cn(
                          'h-4',
                          c.skeleton ??
                            SKELETON_BAR_WIDTHS[(i * 7 + idx * 13) % SKELETON_BAR_WIDTHS.length],
                        )}
                      />
                    </TableCell>
                  ))}
                  {locate && <TableCell className="record-locate" />}
                  {hasActions && (
                    <TableCell className="record-actions pr-4 text-right">
                      {canEdit && (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          className="text-muted-foreground"
                          aria-label="Delete row"
                          disabled
                        >
                          <Trash2 />
                        </Button>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              ))}

            {!initialLoading &&
              rows.map((row) => {
                const id = rowKey(row)
                const isExpanded = expandedIds.has(id)
                return (
                  <Fragment key={id}>
                    <TableRow
                      ref={(element) => {
                        if (element) rowRefs.current.set(id, element)
                        else rowRefs.current.delete(id)
                      }}
                      className={cn('record-row group/data-row', expandOnRowClick && expand && 'cursor-pointer', highlightId === id && 'bg-primary/10')}
                      tabIndex={expandOnRowClick && expand ? 0 : undefined}
                      aria-expanded={expandOnRowClick && expand ? isExpanded : undefined}
                      onClick={expandOnRowClick && expand ? (event) => {
                        if (event.target instanceof Element && event.target.closest('a, button, input, select, textarea, summary, [role="button"], [role="switch"], [role="combobox"], [contenteditable="true"]')) return
                        if (window.getSelection()?.toString()) return
                        toggleExpanded(id)
                      } : undefined}
                      onKeyDown={expandOnRowClick && expand ? (event) => {
                        if (event.target !== event.currentTarget || (event.key !== 'Enter' && event.key !== ' ')) return
                        event.preventDefault()
                        toggleExpanded(id)
                      } : undefined}
                    >
                      {expand && (
                        <TableCell className="record-toggle w-[40px]">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-muted-foreground hover:text-foreground min-[1100px]:size-7 min-[1100px]:p-0"
                            aria-label={isExpanded ? 'Collapse row' : 'Expand row'}
                            aria-expanded={isExpanded}
                            tabIndex={expandOnRowClick ? -1 : undefined}
                            onClick={() => toggleExpanded(id)}
                          >
                            {isExpanded ? <ChevronDown /> : <ChevronRight />}
                            <span className="min-[1100px]:hidden">Details</span>
                          </Button>
                        </TableCell>
                      )}
                      {columns.map((c) => (
                        <TableCell key={c.key} data-column={c.key} data-label={c.label} data-content={c.mobileContent || undefined} className={c.cellClassName}>
                          {c.render
                            ? c.render(row, { expand: () => setExpandedIds((previous) => new Set(previous).add(id)) })
                            : c.value(row)}
                        </TableCell>
                      ))}
                      {locate && (
                        <TableCell className="record-locate text-right" data-active={searching || undefined}>
                          {searching && (
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className="text-muted-foreground hover:text-foreground"
                              aria-label="Jump to this row"
                              title="Jump to this row in the full list"
                              disabled={jumpingId === id}
                              onClick={() => jumpTo(row)}
                            >
                              <ArrowRightToLine />
                            </Button>
                          )}
                        </TableCell>
                      )}
                      {hasActions && (
                        <TableCell className="record-actions pr-4">
                          <div className="flex items-center justify-end gap-1">
                            {rowActions?.(row)}
                            {canEdit && (
                              <Button
                                variant="ghost"
                                size="icon-sm"
                                className="text-muted-foreground hover:text-destructive"
                                aria-label="Delete row"
                                disabled={loading || refreshing}
                                onClick={() => remove(row)}
                              >
                                <Trash2 />
                              </Button>
                            )}
                          </div>
                        </TableCell>
                      )}
                    </TableRow>
                    {expand && isExpanded && (
                      <TableRow className="record-detail hover:bg-transparent">
                        <TableCell colSpan={columnCount} className="bg-muted/20 p-0">
                          <div className="min-w-0 px-2 py-2 wrap-anywhere sm:px-4 sm:py-3">{expand(row)}</div>
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                )
              })}

            {!initialLoading && rows.length === 0 && (
              <TableRow className="record-empty">
                <TableCell
                  colSpan={columnCount}
                  className="h-20 text-center text-muted-foreground"
                >
                  {searching ? 'No rows match your search.' : emptyLabel}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <DataPagination
        {...paginationProps}
        position="bottom"
        onPageChange={(next) => void onPageChange(next, true)}
        onSizeChange={(next) => void onSizeChange(next, true)}
      />
    </div>
  )
}
