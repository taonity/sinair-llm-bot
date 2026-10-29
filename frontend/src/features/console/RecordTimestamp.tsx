import { formatTime } from './format'

export function RecordTimestamp({ value }: { value: string }) {
  const date = new Date(value)
  const fullDate = formatTime(value)
  const compactDate = Number.isNaN(date.getTime()) ? fullDate : date.toLocaleString(undefined, {
    month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  })

  return (
    <time dateTime={value} title={fullDate}>
      <span className="record-date-full">{fullDate}</span>
      <span className="record-date-compact hidden">{compactDate}</span>
    </time>
  )
}
