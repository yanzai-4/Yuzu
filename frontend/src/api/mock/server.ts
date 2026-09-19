import type { StreamHandlers } from '../transport';
import type {
  Agent,
  AgentStatus,
  Card,
  ChatMessage,
  Email,
  EventEnvelope,
  EventPayloads,
  EventType,
  Incident,
  LlmSettingsView,
  ModuleEvent,
  Portfolio,
  TaskListView,
  Ticket,
  Trade,
  User,
  WorkingMemoryView,
} from '../types';
import type { UsageMeter } from './usage';
import { clone, nowText } from './util';

/** v0.0.4 🍊 Everything the mock backend knows (mutable; always cloned before leaving the server). */
export interface MockState {
  roomId: string;
  roomName: string;
  users: Map<string, User>;
  agents: Map<string, Agent>;
  statuses: Map<string, AgentStatus>;
  messages: ChatMessage[];
  cards: Map<string, Card>;
  tickets: Map<string, Ticket>;
  taskLists: Map<string, TaskListView>;
  meter: UsageMeter;
  emails: Email[];
  trades: Trade[];
  portfolios: Map<string, Portfolio>;
  incidents: Incident[];
  settings: LlmSettingsView;
  events: ModuleEvent[];
  workingMemory: Map<string, WorkingMemoryView>;
}

interface Client {
  roomId: string;
  handlers: StreamHandlers;
  heartbeat: ReturnType<typeof setInterval>;
}

const RING_CAPACITY = 3000;
const EVENT_LOG_CAPACITY = 4000;
const HEARTBEAT_MS = 15_000;
/** Same kinds the real hub never replays. */
const TRANSIENT: ReadonlySet<EventType> = new Set(['chat.delta', 'chat.typing', 'agent.status', 'usage.tick']);

/**
 * v0.0.4 🍊 In-memory stand-in for the backend's SseHub + database: owns the state, assigns event
 * cursors, keeps a replay ring and delivers envelopes asynchronously to connected clients.
 */
export class MockServer {
  readonly state: MockState;
  private cursor = Date.now() * 1000;
  /** Events with an id at or below the floor have been evicted from the ring. */
  private floor = this.cursor;
  private seq = 0;
  private readonly ring: EventEnvelope[] = [];
  private readonly clients = new Set<Client>();

  /** v0.0.4 🍊 Wraps a seeded state. */
  constructor(state: MockState) {
    this.state = state;
    this.seq = state.messages.reduce((max, m) => Math.max(max, m.seq), 0);
  }

  /** v0.0.4 🍊 Current event cursor (the bootstrap snapshot's `eventCursor`). */
  get eventCursor(): number {
    return this.cursor;
  }

  /** v0.0.4 🍊 Next chat message sequence number. */
  nextSeq(): number {
    return ++this.seq;
  }

  /** v0.0.4 🍊 Publishes an event to every client (payload cloned; delivery is asynchronous). */
  publish<K extends EventType>(type: K, data: EventPayloads[K], agentId: string | null = null): void {
    const envelope: EventEnvelope<K> = {
      id: ++this.cursor,
      type,
      roomId: type === 'error' ? '*' : this.state.roomId,
      agentId,
      time: nowText(),
      data: clone(data),
    };
    if (type === 'module.event') {
      this.state.events.push(clone(data as ModuleEvent));
      if (this.state.events.length > EVENT_LOG_CAPACITY) this.state.events.splice(0, 500);
    }
    if (!TRANSIENT.has(type)) {
      this.ring.push(envelope as EventEnvelope);
      if (this.ring.length > RING_CAPACITY) this.floor = this.ring.shift()?.id ?? this.floor;
    }
    for (const client of this.clients) this.deliver(client, envelope as EventEnvelope);
  }

  /** v0.0.4 🍊 Opens a stream: hello, then replay after `after` (or resync), then live events. */
  connect(roomId: string, after: number, handlers: StreamHandlers): () => void {
    const client: Client = {
      roomId,
      handlers,
      heartbeat: setInterval(() => this.deliver(client, this.control('heartbeat')), HEARTBEAT_MS),
    };
    this.deliver(client, this.control('hello', `mock-${Math.random().toString(16).slice(2, 10)}`));
    if (after > 0 && after < this.cursor) {
      if (after < this.floor) this.deliver(client, this.control('resync'));
      else for (const envelope of this.ring) if (envelope.id > after) this.deliver(client, envelope);
    }
    this.clients.add(client);
    setTimeout(() => handlers.onOpen(), 0);
    return () => {
      clearInterval(client.heartbeat);
      this.clients.delete(client);
    };
  }

  private deliver(client: Client, envelope: EventEnvelope): void {
    if (envelope.roomId !== '*' && envelope.roomId !== client.roomId) return;
    // Deferred like a network hop; by then the client is registered (or already closed).
    setTimeout(() => {
      if (this.clients.has(client)) client.handlers.onEnvelope(envelope);
    }, 0);
  }

  private control(type: 'hello' | 'heartbeat' | 'resync', connectionId = ''): EventEnvelope {
    const data = type === 'hello' ? { cursor: this.cursor, connectionId } : { cursor: this.cursor };
    return { id: -1, type, roomId: this.state.roomId, agentId: null, time: nowText(), data } as EventEnvelope;
  }
}
