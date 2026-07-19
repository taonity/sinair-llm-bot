export type ConsoleRole = 'NONE' | 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER'
export type AccessStatus = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED'

export interface AccessInfo {
  email: string
  displayName: string
  role: ConsoleRole
  accessStatus: AccessStatus
  requestedRole: ConsoleRole | null
  canView: boolean
  canEdit: boolean
  isAdmin: boolean
  isOwner: boolean
}

export interface UserSummary {
  googleId: string
  email: string
  displayName: string
  role: ConsoleRole
  accessStatus: AccessStatus
  requestedRole: ConsoleRole | null
}

/** A dev-only stub login shortcut returned by /api/dev/stub-users. */
export interface StubLogin {
  registrationId: string
  label: string
}

export interface PendingRequest {
  googleId: string
  email: string
  displayName: string
  requestedRole: ConsoleRole | null
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasMore: boolean
}

export interface PageLocation {
  page: number
}

export interface ChatMessage {
  id: string
  roomTarget: string
  senderLogin: string
  senderMemberId: number
  messageText: string
  messageStyle: string
  sentAt: string
  receivedAt: string
}

export interface ChatEvent {
  id: string
  roomTarget: string
  memberName: string
  memberId: number
  status: string
  eventData: string | null
  eventTime: string
  receivedAt: string
}

export interface OutboundMessage {
  id: string
  roomTarget: string
  messageText: string
  replyToExternalId: string | null
  status: string
  createdAt: string
  claimedAt: string | null
  sentAt: string | null
}

export interface PipelineField {
  label: string
  value: string
}

export interface PipelineAlternative {
  text: string
  chosen: boolean
  fields: PipelineField[]
}

export type PipelineStageStatus = 'OK' | 'STOP' | 'SKIP' | 'PASS' | 'INFO'

export interface PipelineStage {
  key: string
  label: string
  status: PipelineStageStatus
  summary: string
  fields: PipelineField[]
  alternatives: PipelineAlternative[]
}

export interface LlmCallUsage {
  tier: string
  model: string
  tokens: number
  tools: string[]
  hasRequestPayload: boolean
  hasResponsePayload: boolean
}

export interface PipelineRun {
  id: string
  pipelineKey: string
  roomTarget: string
  triggerMessageId: string | null
  triggerSenderLogin: string
  triggerText: string
  outcome: string
  outcomeDetail: string | null
  outboundMessageId: string | null
  stages: PipelineStage[]
  totalTokens: number
  llmUsage: LlmCallUsage[]
  createdAt: string
}

export interface RoomSummary {
  id: string
  roomTarget: string
  summary: string
  messageCount: number
  updatedAt: string
}

export interface AuditLog {
  id: string
  action: string
  targetType: string
  targetId: string | null
  actorEmail: string
  occurredAt: string
}
