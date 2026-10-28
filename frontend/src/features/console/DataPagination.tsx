import type { Ref } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const PAGE_SIZES = [20, 50, 100]

type DataPaginationProps = {
  page: number
  size: number
  rowCount: number
  totalElements?: number
  totalPages?: number
  loading: boolean
  position: 'top' | 'bottom'
  paginationRef?: Ref<HTMLElement>
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

export function DataPagination({
  page,
  size,
  rowCount,
  totalElements,
  totalPages,
  loading,
  position,
  paginationRef,
  onPageChange,
  onSizeChange,
}: DataPaginationProps) {
  const pageCount = Math.max(totalPages ?? 1, 1)
  const firstRow = rowCount > 0 ? page * size + 1 : 0
  const lastRow = rowCount > 0 ? page * size + rowCount : 0

  return (
    <nav
      ref={paginationRef}
      aria-label={`Pagination ${position === 'top' ? 'above' : 'below'} records`}
      aria-busy={loading}
      tabIndex={-1}
      className="data-pagination grid grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-2 text-xs text-muted-foreground sm:flex sm:flex-wrap sm:justify-between"
    >
      <span className="min-w-0 tabular-nums">
        {totalElements === undefined ? 'Loading…' : `${firstRow}–${lastRow} of ${totalElements}`}
      </span>
      <div className="flex items-center gap-2 sm:ml-auto">
        <span className="hidden sm:inline">Rows per page</span>
        <Select
          items={Object.fromEntries(PAGE_SIZES.map((value) => [String(value), String(value)]))}
          value={String(size)}
          onValueChange={(value) => value && onSizeChange(Number(value))}
          disabled={loading}
        >
          <SelectTrigger size="sm" className="h-7 w-16" aria-label="Rows per page">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PAGE_SIZES.map((value) => (
              <SelectItem key={value} value={String(value)}>{value}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex items-center gap-1 sm:gap-2">
        <Button
          variant="outline"
          size="sm"
          aria-label="Previous page"
          disabled={loading || page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft aria-hidden="true" />
          <span className="hidden sm:inline">Previous</span>
        </Button>
        <span
          className="min-w-8 text-center whitespace-nowrap tabular-nums"
          aria-label={`Page ${page + 1} of ${pageCount}`}
          aria-live={position === 'top' ? 'polite' : 'off'}
          aria-atomic="true"
        >
          <span className="hidden sm:inline">Page </span>{page + 1} / {pageCount}
        </span>
        <Button
          variant="outline"
          size="sm"
          aria-label="Next page"
          disabled={loading || totalPages === undefined || page + 1 >= pageCount}
          onClick={() => onPageChange(page + 1)}
        >
          <span className="hidden sm:inline">Next</span>
          <ChevronRight aria-hidden="true" />
        </Button>
      </div>
    </nav>
  )
}
