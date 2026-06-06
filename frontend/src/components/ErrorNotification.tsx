'use client'

type ErrorNotificationProps = {
  message: string
  onClose?: () => void
}

export default function ErrorNotification({ message, onClose }: ErrorNotificationProps) {
  return (
    <div className="error-notification">
      <span>{message}</span>
      {onClose && (
        <button onClick={onClose} aria-label="Close notification">
          ×
        </button>
      )}
    </div>
  )
}
