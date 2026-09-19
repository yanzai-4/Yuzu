import type { EventEnvelope, EventPayloads, EventType } from '../api/types';
import { ChatDraft } from './chat';
import { errorEntry, toastError } from './errors';
import { RoomDraft } from './room';
import { setLlmSettings } from './settings';
import { SimDraft } from './sim';
import { TasksDraft } from './tasks';
import { TraceDraft } from './trace';
import { pushToast } from './ui';
import { setUsage } from './usage';

/** Discriminated union of envelopes so `switch (envelope.type)` narrows `envelope.data`. */
type AnyEnvelope = { [K in EventType]: EventEnvelope<K> }[EventType];

/** Beyond this many errors in one batch, the rest collapse into one summary toast. */
const MAX_ERROR_TOASTS = 3;

/**
 * v0.0.4 🍊 One batch of event applications: domain drafts are created lazily and committed together,
 * so a frame's worth of events costs one store update per touched domain.
 */
export class EventTx {
  private roomDraft: RoomDraft | null = null;
  private chatDraft: ChatDraft | null = null;
  private tasksDraft: TasksDraft | null = null;
  private simDraft: SimDraft | null = null;
  private traceDraft: TraceDraft | null = null;

  /** v0.0.4 🍊 Room / agents / statuses draft. */
  get room(): RoomDraft {
    return (this.roomDraft ??= new RoomDraft());
  }

  /** v0.0.4 🍊 Chat / cards / typing draft. */
  get chat(): ChatDraft {
    return (this.chatDraft ??= new ChatDraft());
  }

  /** v0.0.4 🍊 Task lists / tickets draft. */
  get tasks(): TasksDraft {
    return (this.tasksDraft ??= new TasksDraft());
  }

  /** v0.0.4 🍊 Simulation draft. */
  get sim(): SimDraft {
    return (this.simDraft ??= new SimDraft());
  }

  /** v0.0.4 🍊 Trace / incidents / errors draft. */
  get trace(): TraceDraft {
    return (this.traceDraft ??= new TraceDraft());
  }

  /** v0.0.4 🍊 Commits every touched domain, then shows toasts for the batch's errors. */
  commit(): void {
    this.roomDraft?.commit();
    this.chatDraft?.commit();
    this.tasksDraft?.commit();
    this.simDraft?.commit();
    this.traceDraft?.commit();
    const errors = this.traceDraft?.errors ?? [];
    errors.slice(0, MAX_ERROR_TOASTS).forEach((entry) => toastError(entry.error));
    if (errors.length > MAX_ERROR_TOASTS) {
      pushToast({
        tone: 'error',
        title: `${errors.length - MAX_ERROR_TOASTS} more errors`,
        message: 'Open the Trace tab to see all of them.',
      });
    }
  }
}

/**
 * v0.0.4 🍊 The one reducer: routes a realtime envelope into the domain drafts (upsert by id).
 * Control events (hello / heartbeat / resync) are handled by the RoomStream and ignored here.
 */
export function applyEvent(input: EventEnvelope, tx: EventTx): void {
  const envelope = input as AnyEnvelope;
  switch (envelope.type) {
    case 'chat.message':
      tx.chat.upsertMessage(envelope.data);
      break;
    case 'chat.delta':
      tx.chat.appendDelta(envelope.data.messageId, envelope.data.delta, envelope.data.streamState);
      break;
    case 'chat.card':
      tx.chat.upsertCard(envelope.data);
      break;
    case 'chat.typing':
      tx.chat.setTyping(envelope.data.agentId, envelope.data.typing);
      break;
    case 'user.joined':
      tx.room.upsertUser(envelope.data);
      break;
    case 'agent.upsert':
      tx.room.upsertAgent(envelope.data);
      break;
    case 'agent.removed':
      tx.room.removeAgent(envelope.data.agentId);
      tx.tasks.removeAgent(envelope.data.agentId);
      break;
    case 'agent.status':
      tx.room.upsertStatus(envelope.data);
      break;
    case 'module.event':
      tx.trace.addEvent(envelope.data);
      break;
    case 'task.list':
      tx.tasks.upsertList(envelope.data);
      break;
    case 'ticket.upsert':
      tx.tasks.upsertTicket(envelope.data);
      break;
    case 'usage.tick':
      setUsage(envelope.data);
      break;
    case 'sim.email':
      tx.sim.upsertEmail(envelope.data);
      break;
    case 'sim.trade':
      tx.sim.upsertTrade(envelope.data);
      break;
    case 'sim.portfolio':
      tx.sim.upsertPortfolio(envelope.data);
      break;
    case 'security.incident':
      tx.trace.upsertIncident(envelope.data);
      break;
    case 'settings.changed':
      setLlmSettings(envelope.data);
      break;
    case 'error': {
      const context = typeof envelope.data.details?.context === 'string' ? envelope.data.details.context : null;
      tx.trace.addError(errorEntry(envelope.data, 'stream', context));
      break;
    }
    case 'hello':
    case 'heartbeat':
    case 'resync':
      break;
  }
}

/** v0.0.4 🍊 Applies a batch of envelopes (one animation frame) with a single commit. */
export function applyEvents(envelopes: readonly EventEnvelope[]): void {
  if (envelopes.length === 0) return;
  const tx = new EventTx();
  for (const envelope of envelopes) applyEvent(envelope, tx);
  tx.commit();
}

/**
 * v0.0.4 🍊 Applies a REST response immediately through the same reducer (e.g. the Agent returned by
 * PATCH), so the UI updates without waiting for the matching SSE event (upserts are idempotent).
 */
export function applyLocal<K extends EventType>(type: K, data: EventPayloads[K], agentId: string | null = null): void {
  const envelope = { id: -1, type, roomId: '', agentId, time: '', data } as EventEnvelope<K>;
  applyEvents([envelope as EventEnvelope]);
}
