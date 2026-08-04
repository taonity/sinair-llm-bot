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
  sourceOutboundMessageId: string | null
  sourceOutboundMatch: string | null
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
  toolCalls: ToolCallEntry[]
  hasRequestPayload: boolean
  hasResponsePayload: boolean
  promptTokens: number
  completionTokens: number
  attempt: number
  maxAttempts: number
  status: 'SUCCEEDED' | 'FAILED'
  error: string | null
  iteration: number | null
  totalIterations: number | null
}

export interface ToolCallEntry {
  name: string
  arguments: string
  result: string
  error: boolean
  attempts: ToolCallAttempt[]
  maxAttempts: number
}

export interface ToolCallAttempt {
  attempt: number
  result: string
  error: boolean
}

export interface JsonParseFailure {
  label: string
  attempt: number
  payload: string
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
  jsonParseFailures: JsonParseFailure[]
  configRevisionId: string | null
  contextSources: string[]
  createdAt: string
}

export interface SummaryVersion {
  id: string
  summary: string
  messageCount: number
  createdAt: string
  pipelineRunId: string | null
  detailAvailable: boolean
}

export interface RoomSummary {
  id: string
  roomTarget: string
  summary: string
  messageCount: number
  updatedAt: string
  pipelineRunId: string | null
  detailAvailable: boolean
  history: SummaryVersion[]
}

export interface AuditLog {
  id: string
  action: string
  targetType: string
  targetId: string | null
  actorEmail: string
  occurredAt: string
}

export type ConfigFieldType =
  | 'BOOL'
  | 'INT'
  | 'LONG'
  | 'DOUBLE'
  | 'STRING'
  | 'TEXT'
  | 'ENUM'
  | 'STRING_LIST'

export interface ConfigField {
  key: string
  group: string
  label: string
  type: ConfigFieldType
  min: number | null
  max: number | null
  enumValues: string[]
  defaultValue: unknown
  value: unknown
  overridden: boolean
  resettable: boolean
}

export interface TierInfo {
  name: string
  custom: boolean
  definedInProperties: boolean
  definedInDatabase: boolean
  shadowsDeployedTier: boolean
}

export interface ConfigSchema {
  fields: ConfigField[]
  tiers: TierInfo[]
}
