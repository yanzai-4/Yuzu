import type { EventEnvelope, EventType } from './types';

/** v0.0.4 🍊 Callbacks a realtime transport reports to. */
export interface StreamHandlers {
  /** The connection is open (EventSource `open`). */
  onOpen: () => void;
  /** One parsed envelope (every event type, including hello/heartbeat/resync/error). */
  onEnvelope: (envelope: EventEnvelope) => void;
  /** Connection trouble; `fatal` means the transport gave up and must be reopened by the caller. */
  onDisconnect: (fatal: boolean) => void;
}

/** v0.0.4 🍊 Opens a realtime stream for a room; returns a function that closes it. */
export interface StreamTransport {
  open: (roomId: string, after: number, handlers: StreamHandlers) => () => void;
}

/** Every SSE event name of the contract (a Record keeps the list exhaustive at compile time). */
const EVENT_NAMES: Record<EventType, true> = {
  hello: true,
  heartbeat: true,
  resync: true,
  'chat.message': true,
  'chat.delta': true,
  'chat.card': true,
  'chat.typing': true,
  'user.joined': true,
  'agent.upsert': true,
  'agent.removed': true,
  'agent.status': true,
  'module.event': true,
  'task.list': true,
  'ticket.upsert': true,
  'usage.tick': true,
  'sim.email': true,
  'sim.trade': true,
  'sim.portfolio': true,
  'security.incident': true,
  'settings.changed': true,
  error: true,
};

/** v0.0.4 🍊 All realtime event types, in contract order. */
export const EVENT_TYPES = Object.keys(EVENT_NAMES) as EventType[];

/**
 * v0.0.4 🍊 SSE transport over the browser EventSource (`GET /api/stream?roomId=&after=`).
 *
 * The backend names events by type, so a listener is registered per type. The server-sent `error`
 * event shares its name with EventSource's own connection-error event: a MessageEvent carrying data
 * is the server event, anything else is a connection problem.
 */
export const eventSourceTransport: StreamTransport = {
  open(roomId, after, handlers) {
    const url = `/api/stream?roomId=${encodeURIComponent(roomId)}&after=${after}`;
    const source = new EventSource(url);
    const onMessage = (event: MessageEvent<string>) => {
      let envelope: EventEnvelope;
      try {
        envelope = JSON.parse(event.data) as EventEnvelope;
      } catch {
        return;
      }
      handlers.onEnvelope(envelope);
    };
    for (const type of EVENT_TYPES) {
      if (type !== 'error') source.addEventListener(type, onMessage as EventListener);
    }
    // Some servers also send unnamed messages; they carry the type inside the envelope.
    source.onmessage = onMessage;
    source.addEventListener('error', (event: Event) => {
      if (event instanceof MessageEvent && typeof event.data === 'string' && event.data.length > 0) {
        onMessage(event as MessageEvent<string>);
        return;
      }
      handlers.onDisconnect(source.readyState === EventSource.CLOSED);
    });
    source.onopen = () => handlers.onOpen();
    return () => source.close();
  },
};
