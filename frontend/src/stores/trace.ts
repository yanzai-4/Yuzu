import { create } from 'zustand';
import type { ApiError, Incident, ModuleEvent } from '../api/types';

/** v0.0.4 🍊 Maximum live trace events kept in memory (oldest dropped first). */
export const TRACE_CAP = 2000;
/** v0.0.4 🍊 Maximum incidents / errors kept in memory. */
export const LOG_CAP = 300;

/** v0.0.4 🍊 One failure shown in the Trace tab's error log. */
export interface ErrorEntry {
  key: string;
  source: 'request' | 'stream';
  /** "POST /api/agents" for requests; the failing context for stream errors. */
  context: string | null;
  error: ApiError;
}

/** v0.0.4 🍊 Shape of the trace store. */
export interface TraceState {
  /** Live module events, newest first, capped at TRACE_CAP. */
  events: ModuleEvent[];
  /** Security incidents, newest first. */
  incidents: Incident[];
  /** REST and asynchronous errors, newest first. */
  errors: ErrorEntry[];
  /** Non-null while the live list is paused: the Trace tab shows this frozen copy. */
  frozen: ModuleEvent[] | null;
}

/** v0.0.4 🍊 Module events, security incidents and errors for the Trace tab. */
export const useTraceStore = create<TraceState>()(() => ({ events: [], incidents: [], errors: [], frozen: null }));

/** v0.0.4 🍊 Batched writer of the trace store used by the event reducer. */
export class TraceDraft {
  private readonly incomingEvents: ModuleEvent[] = [];
  private readonly incomingIncidents: Incident[] = [];
  private readonly incomingErrors: ErrorEntry[] = [];

  /** v0.0.4 🍊 Queues a live module event. */
  addEvent(event: ModuleEvent): void {
    this.incomingEvents.push(event);
  }

  /** v0.0.4 🍊 Queues a security incident (upsert by id). */
  upsertIncident(incident: Incident): void {
    this.incomingIncidents.push(incident);
  }

  /** v0.0.4 🍊 Queues an asynchronous error. */
  addError(entry: ErrorEntry): void {
    this.incomingErrors.push(entry);
  }

  /** v0.0.4 🍊 Errors queued in this batch (the reducer turns them into toasts). */
  get errors(): readonly ErrorEntry[] {
    return this.incomingErrors;
  }

  /** v0.0.4 🍊 Prepends everything queued in this batch, enforcing the caps. */
  commit(): void {
    const state = useTraceStore.getState();
    const patch: Partial<TraceState> = {};
    if (this.incomingEvents.length > 0) {
      patch.events = [...this.incomingEvents].reverse().concat(state.events).slice(0, TRACE_CAP);
    }
    if (this.incomingIncidents.length > 0) {
      const incoming = new Map<string, Incident>();
      for (const incident of this.incomingIncidents) incoming.set(incident.id, incident);
      const kept = state.incidents.filter((i) => !incoming.has(i.id));
      patch.incidents = [...incoming.values()].reverse().concat(kept).slice(0, LOG_CAP);
    }
    if (this.incomingErrors.length > 0) {
      patch.errors = [...this.incomingErrors].reverse().concat(state.errors).slice(0, LOG_CAP);
    }
    if (Object.keys(patch).length > 0) useTraceStore.setState(patch);
  }
}

/** v0.0.4 🍊 Appends an error outside of an event batch (failed REST requests). */
export function addErrorEntry(entry: ErrorEntry): void {
  useTraceStore.setState((s) => ({ errors: [entry, ...s.errors].slice(0, LOG_CAP) }));
}

/** v0.0.4 🍊 Clears the error log. */
export function clearErrors(): void {
  useTraceStore.setState({ errors: [] });
}

/** v0.0.4 🍊 Clears the live event list. */
export function clearTraceEvents(): void {
  useTraceStore.setState({ events: [] });
}

/** v0.0.4 🍊 Freezes (keeps a copy of) or resumes the live event list display. */
export function setTracePaused(paused: boolean): void {
  useTraceStore.setState((s) => ({ frozen: paused ? s.events : null }));
}
