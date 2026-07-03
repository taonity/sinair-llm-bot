'use client'

import { useCallback, useEffect, useState } from 'react'
import ErrorNotification from '@/components/ErrorNotification'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { AdminPanel } from './AdminPanel'
import { consoleApi } from './api'
import { DataTab, type Column } from './DataTab'
import { formatTime } from './format'
import { SummariesTab } from './SummariesTab'
import type {
  AccessInfo,
  ChatEvent,
  ChatMessage,
  ConsoleRole,
  OutboundMessage,
} from './types'

const ROLE_BADGE: Record<ConsoleRole, 'default' | 'secondary' | 'outline'> = {
  OWNER: 'default',
  ADMIN: 'default',
  EDITOR: 'secondary',
  VIEWER: 'outline',
  NONE: 'outline',
}

function statusBadge(value: string) {
  return (
    <Badge variant="secondary" className="font-normal">
      {value}
    </Badge>
  )
}

const MESSAGE_COLUMNS: Column<ChatMessage>[] = [
  {
    key: 'sentAt',
    label: 'Sent',
    value: (m) => m.sentAt,
    render: (m) => formatTime(m.sentAt),
    cellClassName: 'overflow-hidden whitespace-nowrap text-muted-foreground tabular-nums',
    headClassName: 'w-[160px]',
    skeleton: 'w-[85%]',
  },
  {
    key: 'senderLogin',
    label: 'Sender',
    value: (m) => m.senderLogin,
    cellClassName: 'truncate font-medium',
    headClassName: 'w-[150px]',
    searchKey: 'senderLogin',
  },
  {
    key: 'messageText',
    label: 'Message',
    value: (m) => m.messageText,
    cellClassName: 'whitespace-normal break-words leading-snug',
    searchKey: 'messageText',
  },
]

const EVENT_COLUMNS: Column<ChatEvent>[] = [
  {
    key: 'eventTime',
    label: 'Time',
    value: (e) => e.eventTime,
    render: (e) => formatTime(e.eventTime),
    cellClassName: 'overflow-hidden whitespace-nowrap text-muted-foreground tabular-nums',
    headClassName: 'w-[160px]',
    skeleton: 'w-[85%]',
  },
  {
    key: 'memberName',
    label: 'Member',
    value: (e) => e.memberName,
    cellClassName: 'truncate font-medium',
    headClassName: 'w-[30%]',
    searchKey: 'memberName',
  },
  {
    key: 'status',
    label: 'Status',
    value: (e) => e.status,
    render: (e) => statusBadge(e.status),
    headClassName: 'w-[20%]',
    skeleton: 'h-5 w-16 rounded-full',
    searchKey: 'status',
  },
  {
    key: 'eventData',
    label: 'Data',
    value: (e) => e.eventData ?? '',
    render: (e) => e.eventData ?? '—',
    cellClassName: 'whitespace-normal break-words leading-snug',
    searchKey: 'eventData',
  },
]

const OUTBOUND_COLUMNS: Column<OutboundMessage>[] = [
  {
    key: 'createdAt',
    label: 'Created',
    value: (m) => m.createdAt,
    render: (m) => formatTime(m.createdAt),
    cellClassName: 'overflow-hidden whitespace-nowrap text-muted-foreground tabular-nums',
    headClassName: 'w-[160px]',
    skeleton: 'w-[85%]',
  },
  {
    key: 'status',
    label: 'Status',
    value: (m) => m.status,
    render: (m) => statusBadge(m.status),
    headClassName: 'w-[120px]',
    skeleton: 'h-5 w-14 rounded-full',
    searchKey: 'status',
  },
  {
    key: 'messageText',
    label: 'Message',
    value: (m) => m.messageText,
    cellClassName: 'whitespace-normal break-words leading-snug',
    searchKey: 'messageText',
  },
  {
    key: 'sentAt',
    label: 'Sent',
    value: (m) => m.sentAt ?? '',
    render: (m) => formatTime(m.sentAt),
    cellClassName: 'overflow-hidden whitespace-nowrap text-muted-foreground tabular-nums',
    headClassName: 'w-[160px]',
    skeleton: 'w-[85%]',
  },
]

type TabKey = 'messages' | 'events' | 'outbound' | 'summaries' | 'admin'

export default function DataConsole() {
  const [access, setAccess] = useState<AccessInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<TabKey>('messages')

  const loadAccess = useCallback(async () => {
    try {
      setAccess(await consoleApi.getAccess())
    } catch {
      setError('Failed to load access information.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadAccess()
  }, [loadAccess])

  if (loading) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-8 w-72" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  if (access && !access.canView) {
    return (
      <div className="flex flex-col gap-3">
        {error && <ErrorNotification message={error} onClose={() => setError(null)} />}
        <AccessGate access={access} onUpdated={setAccess} onError={setError} />
      </div>
    )
  }

  if (!access) {
    return error ? (
      <ErrorNotification message={error} onClose={() => setError(null)} />
    ) : null
  }

  const canEdit = access.canEdit

  const tabItems: Record<string, string> = {
    messages: 'Messages',
    events: 'Events',
    outbound: 'Outbound',
    summaries: 'Summaries',
    ...(access.isAdmin ? { admin: 'Admin' } : {}),
  }

  return (
    <div className="flex flex-col gap-3">
      {error && <ErrorNotification message={error} onClose={() => setError(null)} />}

      <Tabs value={tab} onValueChange={(v) => setTab((v ?? 'messages') as TabKey)}>
        <div className="flex items-center justify-between gap-2">
          {/* Mobile: compact dropdown keeps every tab one tap away without hidden horizontal scroll. */}
          <Select
            items={tabItems}
            value={tab}
            onValueChange={(v) => setTab((v ?? 'messages') as TabKey)}
          >
            <SelectTrigger size="sm" className="h-8 w-36 sm:hidden">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(tabItems).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {/* Tablet and up: full tab bar. */}
          <TabsList variant="line" className="hidden h-9 sm:flex">
            <TabsTrigger value="messages">Messages</TabsTrigger>
            <TabsTrigger value="events">Events</TabsTrigger>
            <TabsTrigger value="outbound">Outbound</TabsTrigger>
            <TabsTrigger value="summaries">Summaries</TabsTrigger>
            {access.isAdmin && <TabsTrigger value="admin">Admin</TabsTrigger>}
          </TabsList>
          <div className="flex items-center gap-2">
            <UpgradeAccessControl access={access} onUpdated={setAccess} onError={setError} />
            <Badge variant={ROLE_BADGE[access.role]}>{access.role}</Badge>
          </div>
        </div>

        <TabsContent value="messages" className="pt-2">
          {tab === 'messages' && (
            <DataTab<ChatMessage>
              columns={MESSAGE_COLUMNS}
              rowKey={(m) => m.id}
              load={(page, size, q, field, direction) =>
                consoleApi.listChatMessages(page, size, q, field, direction)
              }
              locate={(m, size, direction) =>
                consoleApi.locateChatMessage(m.id, size, direction).then((r) => r.page)
              }
              roomAccessor={(m) => m.roomTarget}
              canEdit={canEdit}
              onDelete={(m) => consoleApi.deleteChatMessage(m.id)}
              emptyLabel="No chat messages."
              sortLabel="sent time"
              onError={setError}
            />
          )}
        </TabsContent>

        <TabsContent value="events" className="pt-2">
          {tab === 'events' && (
            <DataTab<ChatEvent>
              columns={EVENT_COLUMNS}
              rowKey={(e) => e.id}
              load={(page, size, q, field, direction) =>
                consoleApi.listChatEvents(page, size, q, field, direction)
              }
              locate={(e, size, direction) =>
                consoleApi.locateChatEvent(e.id, size, direction).then((r) => r.page)
              }
              roomAccessor={(e) => e.roomTarget}
              canEdit={canEdit}
              onDelete={(e) => consoleApi.deleteChatEvent(e.id)}
              emptyLabel="No events."
              sortLabel="event time"
              onError={setError}
            />
          )}
        </TabsContent>

        <TabsContent value="outbound" className="pt-2">
          {tab === 'outbound' && (
            <DataTab<OutboundMessage>
              columns={OUTBOUND_COLUMNS}
              rowKey={(m) => m.id}
              load={(page, size, q, field, direction) =>
                consoleApi.listOutboundMessages(page, size, q, field, direction)
              }
              locate={(m, size, direction) =>
                consoleApi.locateOutboundMessage(m.id, size, direction).then((r) => r.page)
              }
              roomAccessor={(m) => m.roomTarget}
              canEdit={canEdit}
              onDelete={(m) => consoleApi.deleteOutboundMessage(m.id)}
              emptyLabel="No outbound messages."
              sortLabel="created time"
              onError={setError}
            />
          )}
        </TabsContent>

        <TabsContent value="summaries" className="pt-2">
          {tab === 'summaries' && <SummariesTab canEdit={canEdit} onError={setError} />}
        </TabsContent>

        {access.isAdmin && (
          <TabsContent value="admin" className="pt-2">
            {tab === 'admin' && <AdminPanel access={access} onError={setError} />}
          </TabsContent>
        )}
      </Tabs>
    </div>
  )
}

function AccessGate({
  access,
  onUpdated,
  onError,
}: {
  access: AccessInfo
  onUpdated: (info: AccessInfo) => void
  onError: (message: string) => void
}) {
  const [desiredRole, setDesiredRole] = useState<ConsoleRole>('VIEWER')
  const [submitting, setSubmitting] = useState(false)

  const submit = async () => {
    setSubmitting(true)
    try {
      const updated = await consoleApi.requestAccess(desiredRole)
      if (updated) onUpdated(updated)
    } catch {
      onError('Failed to submit access request.')
    } finally {
      setSubmitting(false)
    }
  }

  if (access.accessStatus === 'PENDING') {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Access pending</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Your request for <strong>{access.requestedRole ?? 'access'}</strong>{' '}is awaiting an
          admin&apos;s approval.
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Request access</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <p className="text-sm text-muted-foreground">
          You don&apos;t have access to the data console yet.
          {access.accessStatus === 'REJECTED' && ' Your previous request was rejected.'}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <Select
            items={{ VIEWER: 'Viewer (read-only)', EDITOR: 'Editor (read & modify)' }}
            value={desiredRole}
            onValueChange={(v) => setDesiredRole((v ?? 'VIEWER') as ConsoleRole)}
          >
            <SelectTrigger className="w-[220px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="VIEWER">Viewer (read-only)</SelectItem>
              <SelectItem value="EDITOR">Editor (read &amp; modify)</SelectItem>
            </SelectContent>
          </Select>
          <Button disabled={submitting} onClick={submit}>
            {submitting ? 'Submitting…' : 'Request access'}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

/**
 * Subtle inline control shown next to the role badge for a VIEWER who can read but not edit: lets
 * them request an upgrade to EDITOR, which an admin then approves. Renders nothing for
 * editors/admins/owners.
 */
function UpgradeAccessControl({
  access,
  onUpdated,
  onError,
}: {
  access: AccessInfo
  onUpdated: (info: AccessInfo) => void
  onError: (message: string) => void
}) {
  const [submitting, setSubmitting] = useState(false)
  const [confirming, setConfirming] = useState(false)

  // Only viewers (can view, cannot edit, not admin) may request an upgrade.
  if (!access.canView || access.canEdit || access.isAdmin) return null

  if (access.accessStatus === 'PENDING') {
    return <span className="text-xs text-muted-foreground">Editor request pending</span>
  }

  const requestUpgrade = async () => {
    setSubmitting(true)
    try {
      const updated = await consoleApi.requestAccess('EDITOR')
      if (updated) onUpdated(updated)
    } catch {
      onError('Failed to request upgrade.')
    } finally {
      setSubmitting(false)
      setConfirming(false)
    }
  }

  // Two-step confirm so the request can't be triggered by an accidental click.
  if (confirming) {
    return (
      <div className="flex items-center gap-1">
        <span className="text-xs text-muted-foreground">Request editor access?</span>
        <Button size="xs" disabled={submitting} onClick={requestUpgrade}>
          {submitting ? 'Requesting…' : 'Confirm'}
        </Button>
        <Button
          size="xs"
          variant="ghost"
          className="text-muted-foreground"
          disabled={submitting}
          onClick={() => setConfirming(false)}
        >
          Cancel
        </Button>
      </div>
    )
  }

  return (
    <Button
      size="xs"
      variant="ghost"
      className="text-muted-foreground"
      onClick={() => setConfirming(true)}
    >
      Request editor access
    </Button>
  )
}
