import { getBootstrap, streamTransport } from './client';
import type { EventEnvelope } from './types';
import { applyEvents } from '../stores/applyEvent';
import { applySnapshot } from '../stores/applySnapshot';
import { patchConnection, useConnectionStore } from '../stores/connection';

/** No event (not even a heartbeat, sent every 15 s) for this long means the stream is dead. */
const WATCHDOG_MS = 45_000;
const MAX_BACKOFF_MS = 15_000;
/** Fallback flush when requestAnimationFrame does not run (background tabs). */
const FLUSH_FALLBACK_MS = 500;

type Timer = ReturnType<typeof setTimeout>;

/**
 * v0.0.4 🍊 Keeps one room in sync with the backend.
 *
 * 1. `GET /api/bootstrap` → every store is replaced by the snapshot.
 * 2. One stream `GET /api/stream?roomId=&after=<eventCursor>` is opened.
 * 3. Envelopes are queued and applied once per animation frame through the one reducer.
 * 4. `resync` (cursor too old) re-bootstraps; a dead or closed stream reconnects with back-off,
 *    resuming from the last applied event id.
 */
export class RoomStream {
  private readonly roomId: string;
  private queue: EventEnvelope[] = [];
  private frame = 0;
  private flushTimer: Timer | null = null;
  private retryTimer: Timer | null = null;
  private watchdog: Timer | null = null;
  private closeTransport: (() => void) | null = null;
  private cursor = 0;
  private attempts = 0;
  /** Bumped on every (re)start so late callbacks of an old connection are ignored. */
  private generation = 0;
  private stopped = true;

  /** v0.0.4 🍊 Creates a stream for a room (call start()). */
  constructor(roomId: string) {
    this.roomId = roomId;
  }

  /** v0.0.4 🍊 Bootstraps and opens the stream. */
  start(): void {
    this.stopped = false;
    void this.bootstrap();
  }

  /** v0.0.4 🍊 Closes everything and drops queued events. */
  stop(): void {
    this.stopped = true;
    this.generation++;
    this.disconnect();
    this.clearTimers();
    this.queue = [];
    patchConnection({ status: 'idle' });
  }

  /** v0.0.4 🍊 Throws away local state and re-bootstraps (server `resync` or a manual refresh). */
  resync(): void {
    if (this.stopped) return;
    this.disconnect();
    this.clearTimers();
    this.queue = [];
    void this.bootstrap();
  }

  private async bootstrap(): Promise<void> {
    const generation = ++this.generation;
    patchConnection({ status: useConnectionStore.getState().bootstrapped ? 'reconnecting' : 'connecting' });
    try {
      // Only the first failure of a streak is toasted; automatic retries stay quiet.
      const snapshot = await getBootstrap(this.roomId, this.attempts > 0);
      if (generation !== this.generation || this.stopped) return;
      this.queue = [];
      applySnapshot(snapshot);
      this.cursor = snapshot.eventCursor;
      patchConnection({ bootstrapped: true, cursor: this.cursor });
      this.connect();
    } catch {
      if (generation !== this.generation || this.stopped) return;
      this.scheduleRetry(() => void this.bootstrap());
    }
  }

  private connect(): void {
    this.disconnect();
    const generation = this.generation;
    this.closeTransport = streamTransport().open(this.roomId, this.cursor, {
      onOpen: () => undefined,
      onEnvelope: (envelope) => {
        if (generation === this.generation) this.receive(envelope);
      },
      onDisconnect: (fatal) => {
        if (generation !== this.generation) return;
        patchConnection({ status: 'reconnecting' });
        if (fatal) {
          // The transport gave up (HTTP error, server gone): reopen ourselves from our cursor.
          this.disconnect();
          this.scheduleRetry(() => this.connect());
        }
      },
    });
    this.armWatchdog();
  }

  private receive(envelope: EventEnvelope): void {
    this.armWatchdog();
    switch (envelope.type) {
      case 'hello': {
        this.attempts = 0;
        const data = envelope.data as { connectionId?: string };
        patchConnection({ status: 'connected', attempts: 0, connectionId: data.connectionId ?? null });
        return;
      }
      case 'heartbeat':
        return;
      case 'resync':
        this.resync();
        return;
      default:
        if (envelope.id > this.cursor) this.cursor = envelope.id;
        this.queue.push(envelope);
        this.scheduleFlush();
    }
  }

  private scheduleFlush(): void {
    if (this.frame === 0) this.frame = requestAnimationFrame(this.flush);
    this.flushTimer ??= setTimeout(this.flush, FLUSH_FALLBACK_MS);
  }

  private readonly flush = (): void => {
    if (this.frame !== 0) cancelAnimationFrame(this.frame);
    if (this.flushTimer) clearTimeout(this.flushTimer);
    this.frame = 0;
    this.flushTimer = null;
    const batch = this.queue;
    this.queue = [];
    applyEvents(batch);
    patchConnection({ cursor: this.cursor });
  };

  private scheduleRetry(action: () => void): void {
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.attempts++;
    const delay = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** (this.attempts - 1)) + Math.random() * 400;
    patchConnection({ status: 'reconnecting', attempts: this.attempts });
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      if (!this.stopped) action();
    }, delay);
  }

  private armWatchdog(): void {
    if (this.watchdog) clearTimeout(this.watchdog);
    this.watchdog = setTimeout(() => {
      if (this.stopped) return;
      this.disconnect();
      this.scheduleRetry(() => this.connect());
    }, WATCHDOG_MS);
  }

  private disconnect(): void {
    this.closeTransport?.();
    this.closeTransport = null;
    if (this.watchdog) clearTimeout(this.watchdog);
    this.watchdog = null;
  }

  private clearTimers(): void {
    if (this.frame !== 0) cancelAnimationFrame(this.frame);
    if (this.flushTimer) clearTimeout(this.flushTimer);
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.frame = 0;
    this.flushTimer = null;
    this.retryTimer = null;
  }
}

let activeStream: RoomStream | null = null;

/** v0.0.4 🍊 Starts the room stream (replacing any previous one) and returns a stop function. */
export function startRoomStream(roomId: string): () => void {
  activeStream?.stop();
  const stream = new RoomStream(roomId);
  activeStream = stream;
  stream.start();
  return () => {
    stream.stop();
    if (activeStream === stream) activeStream = null;
  };
}

/** v0.0.4 🍊 Forces a re-bootstrap of the active room stream (e.g. a "Reconnect now" button). */
export function resyncRoom(): void {
  activeStream?.resync();
}
