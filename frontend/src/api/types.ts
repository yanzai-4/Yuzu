/**
 * v0.0.4 🍊 Frozen API contract shared with the backend (mirror of docs/API.md).
 *
 * Times are natural-language strings ("Sat Sep 19, 11:32:05 AM"); ordering uses `seq` or event ids.
 * Change this file and docs/API.md together.
 */

/** v0.0.4 🍊 Stable error codes returned by the backend. */
export type ErrorCode =
  | 'BAD_REQUEST'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'AGENT_LIMIT'
  | 'PERMISSION_DENIED'
  | 'SANDBOX_VIOLATION'
  | 'SECURITY_BLOCKED'
  | 'APPROVAL_REQUIRED'
  | 'NOT_CONFIGURED'
  | 'LLM_AUTH'
  | 'LLM_TRANSPORT'
  | 'LLM_OUTPUT_INVALID'
  | 'TOOL_EXECUTION'
  | 'CANCELLED'
  | 'INTERNAL';

/** v0.0.4 🍊 Error body of every failed request and of `error` events. */
export interface ApiError {
  code: ErrorCode;
  message: string;
  details: Record<string, unknown>;
  agentId?: string | null;
  time: string;
}

/** v0.0.4 🍊 A human member of the room. */
export interface User {
  id: string;
  username: string;
  color: string;
  roomId: string;
}

export type Permission =
  | 'CHAT_POST'
  | 'CHAT_MENTION_ALL'
  | 'ASK_USER'
  | 'TASK_ASSIGN'
  | 'TASK_APPROVE'
  | 'WEB_BROWSE'
  | 'EMAIL_READ'
  | 'EMAIL_SEND'
  | 'TRADE_VIEW'
  | 'TRADE_EXECUTE'
  | 'CODE_WRITE'
  | 'CODE_EXECUTE'
  | 'FILE_READ'
  | 'FILE_WRITE'
  | 'MEMORY_RECALL';

export type RoleKey = 'PROJECT_MANAGER' | 'RESEARCHER' | 'ENGINEER' | 'CUSTOMER_LIAISON' | 'FINANCE_ANALYST';

/** v0.0.4 🍊 Numeric and list limits attached to an agent's permissions. */
export interface Limits {
  emailAllowedDomains: string[];
  emailMaxPerHour: number;
  tradeMaxNotionalUsd: number;
  tradeAutoApproveUsd: number;
  fileQuotaMb: number;
}

/** v0.0.4 🍊 A role preset offered when creating an agent. */
export interface RolePreset {
  role: RoleKey;
  title: string;
  description: string;
  scopeText: string;
  permissions: Permission[];
  limits: Limits;
}

/** v0.0.4 🍊 An AI coworker. `name` is a citrus fruit (Yuzu, Lime, ...), `avatarKey` selects the SVG. */
export interface Agent {
  agentId: string;
  roomId: string;
  name: string;
  avatarKey: string;
  color: string;
  role: RoleKey;
  title: string;
  scopeText: string;
  persona: string;
  permissions: Permission[];
  limits: Limits;
  state: 'ACTIVE' | 'PAUSED' | 'RETIRED';
  createdTime: string;
}

export interface CreateAgentRequest {
  role: RoleKey;
  title?: string;
  scopeText?: string;
  persona?: string;
  permissions?: Permission[];
  limits?: Partial<Limits>;
}

export type UpdateAgentRequest = Partial<Omit<CreateAgentRequest, 'role'>>;

export type MessageKind = 'TEXT' | 'WARNING' | 'QUESTION_CARD' | 'APPROVAL_CARD' | 'REPORT' | 'SYSTEM';

/** v0.0.4 🍊 A group-chat message. WARNING messages render with a yellow background. */
export interface ChatMessage {
  id: string;
  roomId: string;
  seq: number;
  authorKind: 'HUMAN' | 'AGENT' | 'SYSTEM';
  authorId: string;
  authorName: string;
  kind: MessageKind;
  content: string;
  mentions: string[];
  mentionAll: boolean;
  closure: boolean;
  causalDepth: number;
  replyTo?: string | null;
  cardId?: string | null;
  streamState: 'NONE' | 'STREAMING' | 'DONE' | 'STOPPED';
  traceId?: string | null;
  time: string;
}

/** v0.0.4 🍊 A question or approval card; answered through REST, never through chat. */
export interface Card {
  id: string;
  agentId: string;
  roomId: string;
  kind: 'QUESTION' | 'APPROVAL';
  messageId?: string | null;
  prompt: string;
  options: { id: string; label: string }[];
  allowOther: boolean;
  status: 'OPEN' | 'ANSWERED' | 'CANCELLED' | 'EXPIRED';
  answer?: { optionIds: string[]; otherText?: string | null } | null;
  answeredByName?: string | null;
  time: string;
  answeredTime?: string | null;
}

export type ModuleKind =
  | 'CHAT'
  | 'SAFETY'
  | 'BEHAVIOR'
  | 'HIGH_RISK'
  | 'TOOL_CALLING'
  | 'MONITOR'
  | 'MAIN'
  | 'PLANNING'
  | 'COGNITION'
  | 'SUBCONSCIOUS'
  | 'LEARNING'
  | 'MEMORY'
  | 'WM_COMPACTOR'
  | 'TOOL'
  | 'SYSTEM';

export type DeskState = 'IDLE' | 'WORKING' | 'THINKING' | 'TALKING' | 'WAITING' | 'PAUSED' | 'ERROR';

/** v0.0.4 🍊 Live desk state of an agent; `bubble` is shown above its head. */
export interface AgentStatus {
  agentId: string;
  state: DeskState;
  activeModules: ModuleKind[];
  bubble: { module: string; summary: string };
  poolSize: number;
  pendingBatches: number;
  time: string;
}

export type EventPhase = 'START' | 'STATE' | 'END' | 'ERROR' | 'CANCELLED' | 'INFO';

/** v0.0.4 🍊 One monitor/trace event reported by a module. */
export interface ModuleEvent {
  id: string;
  agentId: string;
  module: ModuleKind;
  phase: EventPhase;
  text: string;
  detail?: Record<string, unknown> | null;
  traceId?: string | null;
  spanId?: string | null;
  parentSpanId?: string | null;
  time: string;
}

/** v0.0.30 🍊 One page of an agent's event history plus the cursor of the next (older) page. */
export interface ModuleEventPage {
  events: ModuleEvent[];
  /** Value of the `X-Next-Cursor` response header; null when the history is exhausted. */
  nextCursor: string | null;
}

/** v0.0.30 🍊 Result of a room-wide control action (`stop-all` / `resume-all`). */
export interface RoomControlResult {
  roomId: string;
  action: 'STOP_ALL' | 'RESUME_ALL';
  /** How many coworkers actually changed state. */
  affected: number;
  statuses: AgentStatus[];
  time: string;
}

/** v0.0.30 🍊 One recorded HTTP attempt against the model provider (metadata; the payload is fetched on demand). */
export interface LlmCall {
  id: string;
  agentId: string;
  /** Same name as `ModuleEvent.module`, so a span can be matched to its calls. */
  module: string;
  tier: string;
  model: string;
  strategy: string;
  attempt: number;
  status: 'OK' | 'ERROR';
  error?: string | null;
  traceId?: string | null;
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  reasoningTokens: number;
  estimated: boolean;
  latencyMs: number;
  ttftMs?: number | null;
  /** True when `GET /api/llm-calls/{id}/payload` can serve the raw JSON. */
  hasPayload: boolean;
  time: string;
}

/** v0.0.30 🍊 The exact request and response JSON of one model call (never contains credentials). */
export interface LlmCallPayload {
  id: string;
  agentId: string;
  model: string;
  request: unknown;
  response: unknown;
}

export type TaskItemState = 'TODO' | 'DOING' | 'DONE' | 'STRUCK';

export interface TaskItem {
  id: string;
  ord: number;
  text: string;
  state: TaskItemState;
  note?: string | null;
  struckReason?: string | null;
}

export interface TaskList {
  id: string;
  agentId: string;
  goal: string;
  status: 'ACTIVE' | 'AWAITING_APPROVAL' | 'ARCHIVED';
  publisherId: string;
  publisherName: string;
  ticketId?: string | null;
  outcome?: string | null;
  items: TaskItem[];
  time: string;
  archivedTime?: string | null;
}

/** v0.0.4 🍊 An agent's current task list plus the last archived ones. */
export interface TaskListView {
  agentId: string;
  current: TaskList | null;
  recentArchived: TaskList[];
}

export type TicketStatus = 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'DONE' | 'APPROVED' | 'CANCELLED';

export interface Ticket {
  id: string;
  roomId: string;
  title: string;
  detail: string;
  status: TicketStatus;
  creatorName: string;
  assigneeId?: string | null;
  requesterName?: string | null;
  listId?: string | null;
  time: string;
  updatedTime: string;
}

/** v0.0.4 🍊 Aggregated token usage for one key (agent, module, tier or model). */
export interface UsageRow {
  key: string;
  calls: number;
  attempts: number;
  promptTokens: number;
  cachedTokens: number;
  completionTokens: number;
  reasoningTokens: number;
  errors: number;
  retries: number;
  /** cached / prompt over calls that report caching; null when unknown. */
  hitRate: number | null;
}

export interface UsageSnapshot {
  totals: UsageRow;
  byAgent: UsageRow[];
  byModule: UsageRow[];
  byTier: UsageRow[];
  byModel: UsageRow[];
  localCacheHits: number;
  localCacheLookups: number;
  time: string;
}

export interface Email {
  id: string;
  agentId: string;
  direction: 'IN' | 'OUT';
  from: string;
  to: string;
  subject: string;
  body: string;
  status: 'RECEIVED' | 'SENT' | 'BLOCKED';
  time: string;
}

export interface Trade {
  id: string;
  agentId: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  qty: number;
  price: number;
  notional: number;
  status: 'PENDING_APPROVAL' | 'EXECUTED' | 'REJECTED' | 'BLOCKED';
  reason?: string | null;
  time: string;
}

export interface Portfolio {
  agentId: string;
  cash: number;
  positions: { symbol: string; qty: number; avgPrice: number }[];
}

export interface Incident {
  id: string;
  agentId: string;
  stage: 'INBOUND' | 'OUTBOUND' | 'BEHAVIOR' | 'GUARD' | 'HIGH_RISK';
  verdict: string;
  reasons: string[];
  excerpt: string;
  time: string;
}

export type Tier = 'IMPORTANT' | 'DEFAULT' | 'LIGHT';

export interface TierSettings {
  model: string;
  reasoningEffort?: string | null;
  maxOutputTokens: number;
}

export interface LlmSettingsView {
  provider: 'OPENAI' | 'EDGEONE' | 'CUSTOM';
  baseUrl: string;
  hasKey: boolean;
  apiKeyMasked?: string | null;
  tiers: Record<Tier, TierSettings>;
}

export interface UpdateLlmSettingsRequest {
  provider: LlmSettingsView['provider'];
  baseUrl: string;
  tiers: Record<Tier, TierSettings>;
}

export interface LlmTestResult {
  tiers: Record<Tier, { ok: boolean; model: string; strategy?: string | null; latencyMs?: number | null; error?: string | null }>;
}

export interface WorkingMemoryView {
  agentId: string;
  digest: string | null;
  entries: { id: string; direction: 'IN' | 'OUT'; origin: string; text: string; time: string }[];
}

/** v0.0.4 🍊 Everything the UI needs to render a room, plus the cursor to resume the stream from. */
export interface Snapshot {
  roomId: string;
  roomName: string;
  users: User[];
  agents: Agent[];
  statuses: AgentStatus[];
  messages: ChatMessage[];
  cards: Card[];
  tickets: Ticket[];
  taskLists: TaskListView[];
  usage: UsageSnapshot;
  emails: Email[];
  trades: Trade[];
  portfolios: Portfolio[];
  incidents: Incident[];
  settings: LlmSettingsView;
  eventCursor: number;
  time: string;
}

export type EventType =
  | 'hello'
  | 'heartbeat'
  | 'resync'
  | 'chat.message'
  | 'chat.delta'
  | 'chat.card'
  | 'chat.typing'
  | 'user.joined'
  | 'agent.upsert'
  | 'agent.removed'
  | 'agent.status'
  | 'module.event'
  | 'task.list'
  | 'ticket.upsert'
  | 'usage.tick'
  | 'sim.email'
  | 'sim.trade'
  | 'sim.portfolio'
  | 'security.incident'
  | 'settings.changed'
  | 'error';

/** v0.0.4 🍊 Payload type of each realtime event. */
export interface EventPayloads {
  hello: { cursor: number; connectionId: string };
  heartbeat: { cursor: number };
  resync: { cursor: number };
  'chat.message': ChatMessage;
  'chat.delta': { messageId: string; delta: string; streamState: ChatMessage['streamState'] };
  'chat.card': Card;
  'chat.typing': { agentId: string; typing: boolean };
  'user.joined': User;
  'agent.upsert': Agent;
  'agent.removed': { agentId: string };
  'agent.status': AgentStatus;
  'module.event': ModuleEvent;
  'task.list': TaskListView;
  'ticket.upsert': Ticket;
  'usage.tick': UsageSnapshot;
  'sim.email': Email;
  'sim.trade': Trade;
  'sim.portfolio': Portfolio;
  'security.incident': Incident;
  'settings.changed': LlmSettingsView;
  error: ApiError;
}

/** v0.0.4 🍊 JSON body of every SSE message. */
export interface EventEnvelope<T extends EventType = EventType> {
  id: number;
  type: T;
  roomId: string;
  agentId?: string | null;
  time: string;
  data: EventPayloads[T];
}
