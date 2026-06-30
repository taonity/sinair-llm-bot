import { fetchWithTimeout } from '@/lib/clientApi'
import { getRuntimeConfig } from '@/lib/runtimeConfig'
import { getCookie } from '@/lib/cookies'
import type {
  AccessInfo,
  AuditLog,
  ChatEvent,
  ChatMessage,
  ConsoleRole,
  OutboundMessage,
  PageResponse,
  PageLocation,
  PendingRequest,
  RoomSummary,
  StubLogin,
  UserSummary,
} from './types'

const BASE = '/api/console'

async function csrfToken(): Promise<string> {
  const config = await getRuntimeConfig()
  const cookieName = config.csrfCookieName || 'XSRF-TOKEN'
  return getCookie(cookieName) || ''
}

class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}

async function get<T>(path: string): Promise<T> {
  const res = await fetchWithTimeout(`${BASE}${path}`, { timeoutMs: 10000 })
  if (!res.ok) {
    throw new ApiError(res.status, `Request failed (${res.status})`)
  }
  return res.json() as Promise<T>
}

async function mutate<T>(path: string, method: string, body?: unknown): Promise<T | null> {
  const token = await csrfToken()
  const res = await fetchWithTimeout(`${BASE}${path}`, {
    method,
    headers: {
      'X-XSRF-TOKEN': token,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
    timeoutMs: 10000,
  })
  if (!res.ok) {
    throw new ApiError(res.status, `Request failed (${res.status})`)
  }
  if (res.status === 204) {
    return null
  }
  const text = await res.text()
  return text ? (JSON.parse(text) as T) : null
}

export const consoleApi = {
  ApiError,

  getAccess: () => get<AccessInfo>('/access/me'),

  requestAccess: (requestedRole: ConsoleRole) =>
    mutate<AccessInfo>('/access/request', 'POST', { requestedRole }),

  listPendingRequests: () => get<PendingRequest[]>('/access/requests'),

  approveRequest: (googleId: string, role: ConsoleRole) =>
    mutate(`/access/requests/${encodeURIComponent(googleId)}/approve`, 'POST', { role }),

  rejectRequest: (googleId: string) =>
    mutate(`/access/requests/${encodeURIComponent(googleId)}/reject`, 'POST'),

  listUsers: () => get<UserSummary[]>('/users'),

  changeUserRole: (googleId: string, role: ConsoleRole) =>
    mutate<UserSummary>(`/users/${encodeURIComponent(googleId)}/role`, 'PUT', { role }),

  listChatMessages: (page: number, size: number, q?: string, field?: string) =>
    get<PageResponse<ChatMessage>>(buildListQuery('/chat-messages', page, size, q, field)),

  deleteChatMessage: (id: string) => mutate(`/chat-messages/${encodeURIComponent(id)}`, 'DELETE'),

  locateChatMessage: (id: string, size: number) =>
    get<PageLocation>(`/chat-messages/${encodeURIComponent(id)}/page?size=${size}`),

  listChatEvents: (page: number, size: number, q?: string, field?: string) =>
    get<PageResponse<ChatEvent>>(buildListQuery('/chat-events', page, size, q, field)),

  deleteChatEvent: (id: string) => mutate(`/chat-events/${encodeURIComponent(id)}`, 'DELETE'),

  locateChatEvent: (id: string, size: number) =>
    get<PageLocation>(`/chat-events/${encodeURIComponent(id)}/page?size=${size}`),

  listOutboundMessages: (page: number, size: number, q?: string, field?: string) =>
    get<PageResponse<OutboundMessage>>(buildListQuery('/outbound-messages', page, size, q, field)),

  deleteOutboundMessage: (id: string) =>
    mutate(`/outbound-messages/${encodeURIComponent(id)}`, 'DELETE'),

  locateOutboundMessage: (id: string, size: number) =>
    get<PageLocation>(`/outbound-messages/${encodeURIComponent(id)}/page?size=${size}`),

  listSummaries: () => get<RoomSummary[]>('/summaries'),

  updateSummary: (id: string, summary: string) =>
    mutate<RoomSummary>(`/summaries/${encodeURIComponent(id)}`, 'PUT', { summary }),

  listAuditLogs: (page: number, size: number, q?: string, field?: string) =>
    get<PageResponse<AuditLog>>(buildListQuery('/audit-logs', page, size, q, field)),
}

function buildListQuery(
  path: string,
  page: number,
  size: number,
  q?: string,
  field?: string,
): string {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (q && q.trim()) {
    params.set('q', q.trim())
    if (field && field !== 'all') {
      params.set('field', field)
    }
  }
  return `${path}?${params.toString()}`
}
