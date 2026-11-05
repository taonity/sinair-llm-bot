export function OutboundTimestamp({ value, label, createdAt }: {
  value: string | null
  label: 'Created' | 'Sent'
  createdAt?: string
}) {
  if (!value) return <span title="Not sent">Not sent</span>
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return <span>{label}: {value}</span>
  const sameDay = createdAt && date.toDateString() === new Date(createdAt).toDateString()
  const clock = { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false } as const
  const full = date.toLocaleString(undefined, { year: 'numeric', month: 'short', day: '2-digit', ...clock })
  const compact = date.toLocaleString(undefined, { ...(!sameDay && { month: 'short', day: '2-digit' }), ...clock })

  return (
    <span className="inline-flex items-baseline gap-1">
      <span className="hidden max-[1099px]:inline">{label}</span>
      <time dateTime={value} title={`${label}: ${full}`}>
        <span className="max-[1099px]:hidden">{full}</span>
        <span className="hidden max-[1099px]:inline">{compact}</span>
      </time>
    </span>
  )
}
