'use client'

import { useCallback, useEffect, useMemo, useState } from 'react'
import { RotateCw, Trash2 } from 'lucide-react'
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
  /** Plain-text value used for searching and as the default cell content. */
  value: (row: T) => string
  /** Optional custom cell renderer. */
  render?: (row: T) => React.ReactNode
  cellClassName?: string
  headClassName?: string
  /** Tailwind classes sizing the loading skeleton bar to match real content. */
  skeleton?: string
}

type DataTabProps<T> = {
  columns: Column<T>[]
  rowKey: (row: T) => string
  load: (page: number) => Promise<PageResponse<T>>
  canEdit?: boolean
  onDelete?: (row: T) => Promise<unknown>
  emptyLabel: string
  onError: (message: string) => void
}

const SKELETON_ROWS = 8

/** Varied bar widths so loading rows don't look like a rigid grid. */
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
  canEdit = false,
  onDelete,
  emptyLabel,
  onError,
}: DataTabProps<T>) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<T> | null>(null)
  const [loading, setLoading] = useState(true)
  const [filterColumn, setFilterColumn] = useState('all')
  const [query, setQuery] = useState('')

  const reload = useCallback(
    async (targetPage: number) => {
      setLoading(true)
      try {
        const result = await load(targetPage)
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

  useEffect(() => {
    void reload(0)
  }, [reload])

  const filtered = useMemo(() => {
    if (!data) return []
    const q = query.trim().toLowerCase()
    if (!q) return data.content
    const cols =
      filterColumn === 'all' ? columns : columns.filter((c) => c.key === filterColumn)
    return data.content.filter((row) =>
      cols.some((c) => c.value(row).toLowerCase().includes(q)),
    )
  }, [data, query, filterColumn, columns])

  const columnCount = columns.length + (canEdit ? 1 : 0)

  const filterItems = useMemo(
    () => ({ all: 'All columns', ...Object.fromEntries(columns.map((c) => [c.key, c.label])) }),
    [columns],
  )

  const remove = async (row: T) => {
    if (!onDelete) return
    if (!confirm('Delete this row? This action cannot be undone.')) return
    try {
      await onDelete(row)
      await reload(page)
    } catch {
      onError('Failed to delete row.')
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Select items={filterItems} value={filterColumn} onValueChange={(v) => setFilterColumn(v ?? 'all')}>
            <SelectTrigger size="sm" className="w-[150px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All columns</SelectItem>
              {columns.map((c) => (
                <SelectItem key={c.key} value={c.key}>
                  {c.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            className="h-7 max-w-xs"
            placeholder="Search current page…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {query.trim() && (
            <span className="text-xs text-muted-foreground">
              {filtered.length} match{filtered.length === 1 ? '' : 'es'}
            </span>
          )}
        </div>
        <Button
          variant="ghost"
          size="sm"
          disabled={loading}
          onClick={() => reload(page)}
        >
          <RotateCw className={loading ? 'animate-spin' : ''} />
          Refresh
        </Button>
      </div>

      <div className="overflow-hidden rounded-lg border">
        <Table className="table-fixed">
          <TableHeader>
            <TableRow className="bg-muted/40">
              {columns.map((c) => (
                <TableHead key={c.key} className={c.headClassName}>
                  {c.label}
                </TableHead>
              ))}
              {canEdit && <TableHead className="w-[88px] pr-4 text-right">Actions</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading &&
              Array.from({ length: SKELETON_ROWS }).map((_, i) => (
                <TableRow key={`skeleton-${i}`} className="hover:bg-transparent">
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
              filtered.map((row) => (
                <TableRow key={rowKey(row)}>
                  {columns.map((c) => (
                    <TableCell key={c.key} className={c.cellClassName}>
                      {c.render ? c.render(row) : c.value(row)}
                    </TableCell>
                  ))}
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
              ))}

            {!loading && filtered.length === 0 && (
              <TableRow>
                <TableCell
                  colSpan={columnCount}
                  className="h-20 text-center text-muted-foreground"
                >
                  {query.trim() ? 'No rows match your search.' : emptyLabel}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>{data ? `${data.totalElements} total` : ''}</span>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={loading || page <= 0}
            onClick={() => reload(page - 1)}
          >
            Previous
          </Button>
          <span>Page {page + 1} of {Math.max(data?.totalPages ?? 1, 1)}</span>
          <Button
            variant="outline"
            size="sm"
            disabled={loading || !data || page + 1 >= data.totalPages}
            onClick={() => reload(page + 1)}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  )
}
