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

export type Column<T> = {
  key: string
  label: string
  value: (row: T) => string
  render?: (row: T) => React.ReactNode
  cellClassName?: string
  headClassName?: string
  skeleton?: string
  /** Backend field id this column maps to; when set, the column is offered as a search scope. */
  searchKey?: string
}

type DataTabProps<T> = {
  columns: Column<T>[]
  rowKey: (row: T) => string
  load: (page: number, size: number, q?: string, field?: string, direction?: string) => Promise<PageResponse<T>>
  /** Resolves the page index where a searched row lives in the unfiltered list, enabling the jump action. */
  locate?: (row: T, size: number, direction?: string) => Promise<number>
  /** When set, rows get a chevron toggle that reveals this content in a full-width row below. */
  expand?: (row: T) => React.ReactNode
  /** Reads the room name off a row so it can be shown once instead of as a per-row column. */
  roomAccessor?: (row: T) => string
  canEdit?: boolean
  onDelete?: (row: T) => Promise<unknown>
  emptyLabel: string
  /** Label for the column the rows are ordered by (shown on the sort toggle). Defaults to "time". */
  sortLabel?: string
  onError: (message: string) => void
}

const PAGE_SIZES = [20, 50, 100]
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
  expand,
  roomAccessor,
  canEdit = false,
  onDelete,
  emptyLabel,
  sortLabel = 'time',
  onError,
}: DataTabProps<T>) {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE)
  const [data, setData] = useState<PageResponse<T> | null>(null)
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [activeQuery, setActiveQuery] = useState('')
  const [field, setField] = useState('all')
  const [direction, setDirection] = useState<'desc' | 'asc'>('desc')
  const [highlightId, setHighlightId] = useState<string | null>(null)
  const [jumpingId, setJumpingId] = useState<string | null>(null)
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const highlightTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const reload = useCallback(
    async (targetPage: number, targetSize: number, q: string, searchField: string, dir: string) => {
      setLoading(true)
      try {
        const result = await load(targetPage, targetSize, q.trim() || undefined, searchField, dir)
        setData(result)
        setPage(result.page)
      } catch {
        onError('Failed to load data.')
      } finally {
        setLoading(false)
      }
    },
    [load, onError],
  )

  // Initial load only. Subsequent loads are triggered explicitly by user actions
  // (search, paging, page-size, sort, jump) so that a programmatic search-clear during a
  // jump can't reset the page back to 0.
  useEffect(() => {
    void reload(0, DEFAULT_PAGE_SIZE, '', 'all', 'desc')
  }, [reload])

  useEffect(
    () => () => {
      if (highlightTimer.current) clearTimeout(highlightTimer.current)
      if (searchTimer.current) clearTimeout(searchTimer.current)
    },
    [],
  )

  // Debounce the search box; a new search always starts from the first page.
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

  const onSizeChange = (next: number) => {
    setSize(next)
    void reload(0, next, activeQuery, field, direction)
  }

  const onToggleDirection = () => {
    const next = direction === 'desc' ? 'asc' : 'desc'
    setDirection(next)
    void reload(0, size, activeQuery, field, next)
  }

  const searching = activeQuery.trim().length > 0
  const rows = data?.content ?? []
  const columnCount = columns.length + (expand ? 1 : 0) + (locate ? 1 : 0) + (canEdit ? 1 : 0)
  const firstRow = rows[0]
  const roomName = roomAccessor && firstRow ? roomAccessor(firstRow) : null
  const searchableColumns = columns.filter((c) => c.searchKey)

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

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-2">
          {roomAccessor &&
            (roomName ? (
              <Badge variant="outline" className="h-6 min-w-32 font-normal">
                Room: {roomName}
              </Badge>
            ) : (
              <Badge variant="outline" className="h-6 min-w-32 font-normal">
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
              <SelectTrigger size="sm" className="h-7 w-[150px]">
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
            className="h-7 w-full sm:w-56"
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
        <div className="flex items-center gap-1">
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
            disabled={loading}
            onClick={() => reload(page, size, activeQuery, field, direction)}
          >
            <RotateCw className={loading ? 'animate-spin' : ''} />
            Refresh
          </Button>
        </div>
      </div>

      <div className="overflow-hidden rounded-lg border">
        <Table className="min-w-[720px] table-fixed [&_td]:py-1.5 [&_th]:h-9 [&_tr]:border-border/50">
          <TableHeader>
            <TableRow className="bg-muted/40">
              {expand && <TableHead className="w-[40px]" />}
              {columns.map((c) => (
                <TableHead key={c.key} className={c.headClassName}>
                  {c.label}
                </TableHead>
              ))}
              {locate && <TableHead className="w-[48px]" />}
              {canEdit && <TableHead className="w-[64px] pr-4 text-right">Actions</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading &&
              Array.from({ length: SKELETON_ROWS }).map((_, i) => (
                <TableRow key={`skeleton-${i}`} className="hover:bg-transparent">
                  {expand && <TableCell className="w-[40px]" />}
                  {columns.map((c, idx) => (
                    <TableCell key={c.key} className={c.cellClassName}>
                      <Skeleton
                        className={cn(
                          'h-4',
                          c.skeleton ??
                            SKELETON_BAR_WIDTHS[(i * 7 + idx * 13) % SKELETON_BAR_WIDTHS.length],
                        )}
                      />
                    </TableCell>
                  ))}
                  {locate && <TableCell />}
                  {canEdit && (
                    <TableCell className="pr-4 text-right">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        className="text-muted-foreground"
                        aria-label="Delete row"
                        disabled
                      >
                        <Trash2 />
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))}

            {!loading &&
              rows.map((row) => {
                const id = rowKey(row)
                const isExpanded = expandedIds.has(id)
                return (
                  <Fragment key={id}>
                    <TableRow className={cn(highlightId === id && 'bg-primary/10')}>
                      {expand && (
                        <TableCell className="w-[40px]">
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            className="text-muted-foreground hover:text-foreground"
                            aria-label={isExpanded ? 'Collapse row' : 'Expand row'}
                            aria-expanded={isExpanded}
                            onClick={() => toggleExpanded(id)}
                          >
                            {isExpanded ? <ChevronDown /> : <ChevronRight />}
                          </Button>
                        </TableCell>
                      )}
                      {columns.map((c) => (
                        <TableCell key={c.key} className={c.cellClassName}>
                          {c.render ? c.render(row) : c.value(row)}
                        </TableCell>
                      ))}
                      {locate && (
                        <TableCell className="text-right">
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
                      {canEdit && (
                        <TableCell className="pr-4 text-right">
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            className="text-muted-foreground hover:text-destructive"
                            aria-label="Delete row"
                            onClick={() => remove(row)}
                          >
                            <Trash2 />
                          </Button>
                        </TableCell>
                      )}
                    </TableRow>
                    {expand && isExpanded && (
                      <TableRow className="hover:bg-transparent">
                        <TableCell colSpan={columnCount} className="bg-muted/20 p-0">
                          <div className="px-4 py-3">{expand(row)}</div>
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                )
              })}

            {!loading && rows.length === 0 && (
              <TableRow>
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

      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          <span>{data ? `${data.totalElements} total` : ''}</span>
          <span className="text-border">·</span>
          <span>Rows per page</span>
          <Select
            items={Object.fromEntries(PAGE_SIZES.map((s) => [String(s), String(s)]))}
            value={String(size)}
            onValueChange={(v) => v && onSizeChange(Number(v))}
          >
            <SelectTrigger size="sm" className="h-7 w-[72px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PAGE_SIZES.map((s) => (
                <SelectItem key={s} value={String(s)}>
                  {s}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={loading || page <= 0}
            onClick={() => reload(page - 1, size, activeQuery, field, direction)}
          >
            Previous
          </Button>
          <span>Page {page + 1} of {Math.max(data?.totalPages ?? 1, 1)}</span>
          <Button
            variant="outline"
            size="sm"
            disabled={loading || !data || page + 1 >= data.totalPages}
            onClick={() => reload(page + 1, size, activeQuery, field, direction)}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  )
}
