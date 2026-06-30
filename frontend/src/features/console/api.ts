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

  listChatMessages: (page: number) =>
    get<PageResponse<ChatMessage>>(buildListQuery('/chat-messages', page)),

  deleteChatMessage: (id: string) => mutate(`/chat-messages/${encodeURIComponent(id)}`, 'DELETE'),

  listChatEvents: (page: number) =>
    get<PageResponse<ChatEvent>>(buildListQuery('/chat-events', page)),

  deleteChatEvent: (id: string) => mutate(`/chat-events/${encodeURIComponent(id)}`, 'DELETE'),

  listOutboundMessages: (page: number) =>
    get<PageResponse<OutboundMessage>>(buildListQuery('/outbound-messages', page)),

  deleteOutboundMessage: (id: string) =>
    mutate(`/outbound-messages/${encodeURIComponent(id)}`, 'DELETE'),

  listSummaries: () => get<RoomSummary[]>('/summaries'),

  updateSummary: (id: string, summary: string) =>
    mutate<RoomSummary>(`/summaries/${encodeURIComponent(id)}`, 'PUT', { summary }),

  listAuditLogs: (page: number) =>
    get<PageResponse<AuditLog>>(`/audit-logs?page=${page}&size=50`),
}

function buildListQuery(path: string, page: number): string {
  const params = new URLSearchParams({ page: String(page), size: '50' })
  return `${path}?${params.toString()}`
}
