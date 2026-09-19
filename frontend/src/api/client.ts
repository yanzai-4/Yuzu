import { request } from './http';
import { eventSourceTransport, type StreamTransport } from './transport';
import type {
  Agent,
  AgentStatus,
  Card,
  ChatMessage,
  CreateAgentRequest,
  Email,
  LlmSettingsView,
  LlmTestResult,
  ModuleEvent,
  Portfolio,
  RolePreset,
  Snapshot,
  TaskListView,
  Ticket,
  Trade,
  UpdateAgentRequest,
  UpdateLlmSettingsRequest,
  UsageSnapshot,
  User,
  WorkingMemoryView,
} from './types';

/** v0.0.4 🍊 Body of `POST /api/cards/{cardId}/answer`. */
export interface AnswerCardRequest {
  userId: string;
  optionIds: string[];
  otherText?: string;
}

/** v0.0.4 🍊 Every REST endpoint of docs/API.md (implemented over HTTP and by the mock backend). */
export interface YuzuApi {
  joinSession(username: string): Promise<User>;
  /** `silent` skips the error toast (used for automatic reconnect retries). */
  getBootstrap(roomId: string, silent?: boolean): Promise<Snapshot>;
  listMessages(roomId: string, beforeSeq?: number, limit?: number): Promise<ChatMessage[]>;
  postMessage(roomId: string, userId: string, content: string): Promise<ChatMessage>;
  answerCard(cardId: string, body: AnswerCardRequest): Promise<Card>;
  listRoles(): Promise<RolePreset[]>;
  listAgents(roomId: string): Promise<Agent[]>;
  createAgent(roomId: string, body: CreateAgentRequest): Promise<Agent>;
  updateAgent(agentId: string, body: UpdateAgentRequest): Promise<Agent>;
  retireAgent(agentId: string): Promise<void>;
  pauseAgent(agentId: string): Promise<AgentStatus>;
  resumeAgent(agentId: string): Promise<AgentStatus>;
  interruptAgent(agentId: string): Promise<AgentStatus>;
  getWorkingMemory(agentId: string): Promise<WorkingMemoryView>;
  getAgentTasks(agentId: string): Promise<TaskListView>;
  listAgentEvents(agentId: string, beforeSeq?: number, limit?: number): Promise<ModuleEvent[]>;
  listTickets(roomId: string): Promise<Ticket[]>;
  approveTaskList(listId: string, userId: string): Promise<TaskListView>;
  getLlmSettings(): Promise<LlmSettingsView>;
  updateLlmSettings(body: UpdateLlmSettingsRequest): Promise<LlmSettingsView>;
  putLlmKey(apiKey: string): Promise<LlmSettingsView>;
  testLlm(): Promise<LlmTestResult>;
  listModels(): Promise<string[]>;
  getUsage(): Promise<UsageSnapshot>;
  getTrace(traceId: string): Promise<ModuleEvent[]>;
  listEmails(): Promise<Email[]>;
  listTrades(): Promise<Trade[]>;
  listPortfolios(): Promise<Portfolio[]>;
  seedDemo(roomId: string): Promise<Agent[]>;
}

/** v0.0.4 🍊 A complete backend: REST endpoints plus the realtime transport. */
export interface YuzuBackend {
  api: YuzuApi;
  transport: StreamTransport;
}

const enc = encodeURIComponent;

/** v0.0.4 🍊 The real backend over fetch + EventSource. */
export const httpApi: YuzuApi = {
  joinSession: (username) => request('/api/session/join', { method: 'POST', body: { username } }),
  getBootstrap: (roomId, silent) => request('/api/bootstrap', { query: { roomId }, silent }),
  listMessages: (roomId, beforeSeq, limit = 50) =>
    request(`/api/rooms/${enc(roomId)}/messages`, { query: { beforeSeq, limit } }),
  postMessage: (roomId, userId, content) =>
    request(`/api/rooms/${enc(roomId)}/messages`, { method: 'POST', body: { userId, content } }),
  answerCard: (cardId, body) => request(`/api/cards/${enc(cardId)}/answer`, { method: 'POST', body }),
  listRoles: () => request('/api/roles'),
  listAgents: (roomId) => request(`/api/rooms/${enc(roomId)}/agents`),
  createAgent: (roomId, body) => request(`/api/rooms/${enc(roomId)}/agents`, { method: 'POST', body }),
  updateAgent: (agentId, body) => request(`/api/agents/${enc(agentId)}`, { method: 'PATCH', body }),
  retireAgent: (agentId) => request(`/api/agents/${enc(agentId)}`, { method: 'DELETE' }),
  pauseAgent: (agentId) => request(`/api/agents/${enc(agentId)}/pause`, { method: 'POST' }),
  resumeAgent: (agentId) => request(`/api/agents/${enc(agentId)}/resume`, { method: 'POST' }),
  interruptAgent: (agentId) => request(`/api/agents/${enc(agentId)}/interrupt`, { method: 'POST' }),
  getWorkingMemory: (agentId) => request(`/api/agents/${enc(agentId)}/working-memory`),
  getAgentTasks: (agentId) => request(`/api/agents/${enc(agentId)}/tasks`),
  listAgentEvents: (agentId, beforeSeq, limit = 100) =>
    request(`/api/agents/${enc(agentId)}/events`, { query: { beforeSeq, limit } }),
  listTickets: (roomId) => request(`/api/rooms/${enc(roomId)}/tickets`),
  approveTaskList: (listId, userId) =>
    request(`/api/task-lists/${enc(listId)}/approve`, { method: 'POST', body: { userId } }),
  getLlmSettings: () => request('/api/settings/llm'),
  updateLlmSettings: (body) => request('/api/settings/llm', { method: 'PUT', body }),
  putLlmKey: (apiKey) => request('/api/settings/llm/key', { method: 'PUT', body: { apiKey } }),
  testLlm: () => request('/api/settings/llm/test', { method: 'POST' }),
  listModels: () => request('/api/settings/llm/models'),
  getUsage: () => request('/api/usage'),
  getTrace: (traceId) => request(`/api/traces/${enc(traceId)}`),
  listEmails: () => request('/api/sim/emails'),
  listTrades: () => request('/api/sim/trades'),
  listPortfolios: () => request('/api/sim/portfolios'),
  seedDemo: (roomId) => request('/api/demo/seed', { method: 'POST', query: { roomId } }),
};

/** v0.0.4 🍊 True when the build runs against the in-memory mock backend (VITE_MOCK=1). */
export const IS_MOCK = import.meta.env.VITE_MOCK === '1';

let active: YuzuBackend = { api: httpApi, transport: eventSourceTransport };

/** v0.0.4 🍊 Installs the mock backend when VITE_MOCK=1; the mock chunk is never bundled otherwise. */
export async function initBackend(): Promise<void> {
  if (import.meta.env.VITE_MOCK === '1') {
    const { createMockBackend } = await import('./mock');
    active = createMockBackend();
  }
}

/** v0.0.4 🍊 The realtime transport of the active backend (EventSource or the mock stream). */
export function streamTransport(): StreamTransport {
  return active.transport;
}

const api = (): YuzuApi => active.api;

/** v0.0.4 🍊 POST /api/session/join — join (or re-join) the workspace with a username. */
export const joinSession = (username: string) => api().joinSession(username);
/** v0.0.4 🍊 GET /api/bootstrap — full room snapshot plus the stream cursor. */
export const getBootstrap = (roomId: string, silent?: boolean) => api().getBootstrap(roomId, silent);
/** v0.0.4 🍊 GET /api/rooms/{roomId}/messages — older messages, ascending. */
export const listMessages = (roomId: string, beforeSeq?: number, limit?: number) =>
  api().listMessages(roomId, beforeSeq, limit);
/** v0.0.4 🍊 POST /api/rooms/{roomId}/messages — post a chat message as a human. */
export const postMessage = (roomId: string, userId: string, content: string) =>
  api().postMessage(roomId, userId, content);
/** v0.0.4 🍊 POST /api/cards/{cardId}/answer — answer a question or approval card. */
export const answerCard = (cardId: string, body: AnswerCardRequest) => api().answerCard(cardId, body);
/** v0.0.4 🍊 GET /api/roles — role presets for the hire dialog. */
export const listRoles = () => api().listRoles();
/** v0.0.4 🍊 GET /api/rooms/{roomId}/agents — agents of a room. */
export const listAgents = (roomId: string) => api().listAgents(roomId);
/** v0.0.4 🍊 POST /api/rooms/{roomId}/agents — hire an agent (citrus name assigned by the backend). */
export const createAgent = (roomId: string, body: CreateAgentRequest) => api().createAgent(roomId, body);
/** v0.0.4 🍊 PATCH /api/agents/{agentId} — edit profile, permissions or limits. */
export const updateAgent = (agentId: string, body: UpdateAgentRequest) => api().updateAgent(agentId, body);
/** v0.0.4 🍊 DELETE /api/agents/{agentId} — retire an agent. */
export const retireAgent = (agentId: string) => api().retireAgent(agentId);
/** v0.0.4 🍊 POST /api/agents/{agentId}/pause. */
export const pauseAgent = (agentId: string) => api().pauseAgent(agentId);
/** v0.0.4 🍊 POST /api/agents/{agentId}/resume. */
export const resumeAgent = (agentId: string) => api().resumeAgent(agentId);
/** v0.0.4 🍊 POST /api/agents/{agentId}/interrupt. */
export const interruptAgent = (agentId: string) => api().interruptAgent(agentId);
/** v0.0.4 🍊 GET /api/agents/{agentId}/working-memory. */
export const getWorkingMemory = (agentId: string) => api().getWorkingMemory(agentId);
/** v0.0.4 🍊 GET /api/agents/{agentId}/tasks. */
export const getAgentTasks = (agentId: string) => api().getAgentTasks(agentId);
/** v0.0.4 🍊 GET /api/agents/{agentId}/events — recent trace events of one agent. */
export const listAgentEvents = (agentId: string, beforeSeq?: number, limit?: number) =>
  api().listAgentEvents(agentId, beforeSeq, limit);
/** v0.0.4 🍊 GET /api/rooms/{roomId}/tickets. */
export const listTickets = (roomId: string) => api().listTickets(roomId);
/** v0.0.4 🍊 POST /api/task-lists/{listId}/approve — approve and archive a finished list. */
export const approveTaskList = (listId: string, userId: string) => api().approveTaskList(listId, userId);
/** v0.0.4 🍊 GET /api/settings/llm. */
export const getLlmSettings = () => api().getLlmSettings();
/** v0.0.4 🍊 PUT /api/settings/llm — provider, base URL and tiers. */
export const updateLlmSettings = (body: UpdateLlmSettingsRequest) => api().updateLlmSettings(body);
/** v0.0.4 🍊 PUT /api/settings/llm/key — store the API key (only a mask comes back). */
export const putLlmKey = (apiKey: string) => api().putLlmKey(apiKey);
/** v0.0.4 🍊 POST /api/settings/llm/test — ping every tier. */
export const testLlm = () => api().testLlm();
/** v0.0.4 🍊 GET /api/settings/llm/models — model ids offered by the provider. */
export const listModels = () => api().listModels();
/** v0.0.4 🍊 GET /api/usage. */
export const getUsage = () => api().getUsage();
/** v0.0.4 🍊 GET /api/traces/{traceId} — every event of one trace. */
export const getTrace = (traceId: string) => api().getTrace(traceId);
/** v0.0.4 🍊 GET /api/sim/emails. */
export const listEmails = () => api().listEmails();
/** v0.0.4 🍊 GET /api/sim/trades. */
export const listTrades = () => api().listTrades();
/** v0.0.4 🍊 GET /api/sim/portfolios. */
export const listPortfolios = () => api().listPortfolios();
/** v0.0.4 🍊 POST /api/demo/seed — create the four demo agents if missing. */
export const seedDemo = (roomId: string) => api().seedDemo(roomId);
