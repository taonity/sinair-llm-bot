'use client'

import { useCallback, useEffect, useState } from 'react'
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { consoleApi } from './api'
import { DataTab, type Column } from './DataTab'
import { formatTime } from './format'
import type { AccessInfo, AuditLog, ConsoleRole, PendingRequest, UserSummary } from './types'

const AUDIT_COLUMNS: Column<AuditLog>[] = [
  {
    key: 'occurredAt',
    label: 'When',
    value: (a) => a.occurredAt,
    render: (a) => formatTime(a.occurredAt),
    cellClassName: 'whitespace-nowrap text-muted-foreground tabular-nums',
    headClassName: 'w-[170px]',
    skeleton: 'w-[85%]',
  },
  {
    key: 'action',
    label: 'Action',
    value: (a) => a.action,
    cellClassName: 'truncate font-medium',
    headClassName: 'w-[160px]',
  },
  {
    key: 'targetType',
    label: 'Target',
    value: (a) => a.targetType,
    cellClassName: 'truncate',
    headClassName: 'w-[130px]',
  },
  {
    key: 'targetId',
    label: 'Record id',
    value: (a) => a.targetId ?? '',
    render: (a) => <span className="font-mono text-xs">{a.targetId ?? '—'}</span>,
    cellClassName: 'truncate',
    headClassName: 'w-[150px]',
  },
  { key: 'actorEmail', label: 'Actor', value: (a) => a.actorEmail, cellClassName: 'truncate' },
]

export function AdminPanel({
  access,
  onError,
}: {
  access: AccessInfo
  onError: (message: string) => void
}) {
  const [requests, setRequests] = useState<PendingRequest[] | null>(null)
  const [grantRoles, setGrantRoles] = useState<Record<string, ConsoleRole>>({})
  const [busyId, setBusyId] = useState<string | null>(null)

  const loadRequests = useCallback(async () => {
    try {
      const result = await consoleApi.listPendingRequests()
      setRequests(result)
      setGrantRoles(
        Object.fromEntries(
          result.map((r) => [r.googleId, (r.requestedRole ?? 'VIEWER') as ConsoleRole]),
        ),
      )
    } catch {
      onError('Failed to load access requests.')
    }
  }, [onError])

  useEffect(() => {
    void loadRequests()
  }, [loadRequests])

  const decide = async (req: PendingRequest, approve: boolean) => {
    setBusyId(req.googleId)
    try {
      if (approve) {
        await consoleApi.approveRequest(req.googleId, grantRoles[req.googleId] ?? 'VIEWER')
      } else {
        await consoleApi.rejectRequest(req.googleId)
      }
      await loadRequests()
    } catch {
      onError(approve ? 'Failed to approve request.' : 'Failed to reject request.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Pending access requests</CardTitle>
        </CardHeader>
        <CardContent>
          {!requests && (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
            </div>
          )}
          {requests && requests.length === 0 && (
            <p className="text-sm text-muted-foreground">No pending requests.</p>
          )}
          {requests && requests.length > 0 && (
            <div className="overflow-hidden rounded-lg border">
              <Table>
                <TableHeader>
                  <TableRow className="bg-muted/40">
                    <TableHead>User</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Requested</TableHead>
                    <TableHead>Grant</TableHead>
                    <TableHead className="text-right">Decision</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {requests.map((r) => (
                    <TableRow key={r.googleId}>
                      <TableCell className="font-medium">{r.displayName}</TableCell>
                      <TableCell className="text-muted-foreground">{r.email}</TableCell>
                      <TableCell>{r.requestedRole ?? '—'}</TableCell>
                      <TableCell>
                        <Select
                          items={{ VIEWER: 'Viewer', EDITOR: 'Editor' }}
                          value={grantRoles[r.googleId] ?? 'VIEWER'}
                          onValueChange={(v) =>
                            setGrantRoles((prev) => ({
                              ...prev,
                              [r.googleId]: (v ?? 'VIEWER') as ConsoleRole,
                            }))
                          }
                        >
                          <SelectTrigger size="sm" className="w-[120px]">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="VIEWER">Viewer</SelectItem>
                            <SelectItem value="EDITOR">Editor</SelectItem>
                          </SelectContent>
                        </Select>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button
                            size="sm"
                            disabled={busyId === r.googleId}
                            onClick={() => decide(r, true)}
                          >
                            Approve
                          </Button>
                          <Button
                            size="sm"
                            variant="destructive"
                            disabled={busyId === r.googleId}
                            onClick={() => decide(r, false)}
                          >
                            Reject
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      <UsersCard access={access} onError={onError} />

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Audit log</CardTitle>
          <p className="text-xs text-muted-foreground">
            Retained for 2 weeks. Records what changed and by whom — never the changed data.
          </p>
        </CardHeader>
        <CardContent>
          <DataTab<AuditLog>
            columns={AUDIT_COLUMNS}
            rowKey={(a) => a.id}
            load={(page) => consoleApi.listAuditLogs(page)}
            emptyLabel="No audit entries."
            onError={onError}
          />
        </CardContent>
      </Card>
    </div>
  )
}

const ROLE_LABELS: Record<ConsoleRole, string> = {
  NONE: 'No access',
  VIEWER: 'Viewer',
  EDITOR: 'Editor',
  ADMIN: 'Admin',
  OWNER: 'Owner',
}

/** Admin/owner user list with inline role management. */
function UsersCard({
  access,
  onError,
}: {
  access: AccessInfo
  onError: (message: string) => void
}) {
  const [users, setUsers] = useState<UserSummary[] | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setUsers(await consoleApi.listUsers())
    } catch {
      onError('Failed to load users.')
    }
  }, [onError])

  useEffect(() => {
    void load()
  }, [load])

  // Owners can assign any role; admins only up to EDITOR.
  const assignableRoles: ConsoleRole[] = access.isOwner
    ? ['NONE', 'VIEWER', 'EDITOR', 'ADMIN', 'OWNER']
    : ['NONE', 'VIEWER', 'EDITOR']

  const roleItems = Object.fromEntries(
    assignableRoles.map((r) => [r, ROLE_LABELS[r]]),
  ) as Record<string, string>

  const changeRole = async (user: UserSummary, role: ConsoleRole) => {
    if (role === user.role) return
    setBusyId(user.googleId)
    try {
      const updated = await consoleApi.changeUserRole(user.googleId, role)
      if (updated) {
        setUsers((prev) => (prev ?? []).map((u) => (u.googleId === updated.googleId ? updated : u)))
      }
    } catch {
      onError('Failed to change role. Admin roles can only be managed by the owner.')
      void load()
    } finally {
      setBusyId(null)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Users</CardTitle>
        <p className="text-xs text-muted-foreground">
          {access.isOwner
            ? 'You can assign any role, including admins.'
            : 'You can manage roles up to Editor. Admin roles are managed by the owner.'}
        </p>
      </CardHeader>
      <CardContent>
        {!users && (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        )}
        {users && users.length === 0 && (
          <p className="text-sm text-muted-foreground">No users yet.</p>
        )}
        {users && users.length > 0 && (
          <div className="overflow-hidden rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow className="bg-muted/40">
                  <TableHead>User</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-[180px]">Role</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((u) => {
                  const isSelf = u.email === access.email
                  // Only the owner may change an existing admin/owner.
                  const targetIsAdmin = u.role === 'ADMIN' || u.role === 'OWNER'
                  const locked = isSelf || (targetIsAdmin && !access.isOwner)
                  return (
                    <TableRow key={u.googleId}>
                      <TableCell className="font-medium">{u.displayName}</TableCell>
                      <TableCell className="text-muted-foreground">{u.email}</TableCell>
                      <TableCell>
                        <Badge variant="secondary" className="font-normal">
                          {u.accessStatus}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {locked ? (
                          <Badge variant="outline">{ROLE_LABELS[u.role]}</Badge>
                        ) : (
                          <Select
                            items={roleItems}
                            value={u.role}
                            onValueChange={(v) => v && changeRole(u, v as ConsoleRole)}
                          >
                            <SelectTrigger size="sm" className="w-[160px]" disabled={busyId === u.googleId}>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {assignableRoles.map((r) => (
                                <SelectItem key={r} value={r}>
                                  {ROLE_LABELS[r]}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        )}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
