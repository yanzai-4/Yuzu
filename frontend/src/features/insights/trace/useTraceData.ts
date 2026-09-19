import { useCallback, useMemo, useState } from 'react';
import { listAgentEventPage, listTraceLlmCalls } from '../../../api/client';
import type { Incident, LlmCall, ModuleEvent, ModuleEventPage } from '../../../api/types';
import { useAsync } from '../../../lib/useAsync';
import type { PersonRef } from '../../../stores/people';
import { useRoomStore } from '../../../stores/room';
import { usePeople } from '../../../stores/selectors';
import { useTraceStore, type ErrorEntry } from '../../../stores/trace';

/** v0.0.4 🍊 Filters of the live event list ('ALL' = no filter). */
export interface TraceFilter {
  agentId: string;
  module: string;
  phase: string;
}

/** v0.0.4 🍊 No filter at all. */
export const NO_FILTER: TraceFilter = { agentId: 'ALL', module: 'ALL', phase: 'ALL' };

/** v0.0.4 🍊 The live events (or the paused snapshot) after filtering, newest first. */
export function useFilteredEvents(filter: TraceFilter): { events: ModuleEvent[]; total: number; paused: boolean } {
  const live = useTraceStore((s) => s.events);
  const frozen = useTraceStore((s) => s.frozen);
  const source = frozen ?? live;
  const events = useMemo(
    () =>
      source.filter(
        (e) =>
          (filter.agentId === 'ALL' || e.agentId === filter.agentId) &&
          (filter.module === 'ALL' || e.module === filter.module) &&
          (filter.phase === 'ALL' || e.phase === filter.phase),
      ),
    [source, filter.agentId, filter.module, filter.phase],
  );
  return { events, total: source.length, paused: frozen !== null };
}

/** v0.0.4 🍊 Agents offered by the agent filter (retired ones included, sorted by name). */
export function useAgentFilterOptions(): { id: string; label: string }[] {
  const agents = useRoomStore((s) => s.agents);
  return useMemo(
    () =>
      Object.values(agents)
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((a) => ({ id: a.agentId, label: a.state === 'RETIRED' ? `${a.name} (retired)` : a.name })),
    [agents],
  );
}

/** v0.0.30 🍊 One agent's persisted history, paged backwards with the server's opaque cursor. */
export interface AgentHistory {
  events: ModuleEvent[];
  /** Cursor of the next (older) page; null when the history is exhausted. */
  nextCursor: string | null;
  loading: boolean;
  failed: boolean;
  loadOlder: () => void;
  reload: () => void;
}

/**
 * v0.0.30 🍊 Loads `GET /api/agents/{id}/events` page by page, following the `X-Next-Cursor` header the
 * backend returns. Switching agents starts over; late answers of a previous agent are ignored.
 */
export function useAgentHistory(agentId: string, pageSize = 50): AgentHistory {
  const empty: ModuleEventPage = useMemo(() => ({ events: [], nextCursor: null }), []);
  const first = useAsync(() => (agentId ? listAgentEventPage(agentId, null, pageSize) : Promise.resolve(empty)), agentId);
  // Pages after the first are appended here; they are discarded as soon as another agent is picked.
  const [older, setOlder] = useState<{
    key: string;
    events: ModuleEvent[];
    nextCursor: string | null;
    loading: boolean;
    failed: boolean;
  }>({ key: agentId, events: [], nextCursor: null, loading: false, failed: false });

  const mine = useMemo(
    () => (older.key === agentId ? older : { key: agentId, events: [], nextCursor: null, loading: false, failed: false }),
    [older, agentId],
  );
  const page = first.data ?? empty;
  const events = useMemo(() => {
    const seen = new Set(page.events.map((e) => e.id));
    return [...page.events, ...mine.events.filter((e) => !seen.has(e.id))];
  }, [page.events, mine.events]);
  const nextCursor = mine.events.length > 0 ? mine.nextCursor : page.nextCursor;

  const loadOlder = useCallback(() => {
    if (!agentId || !nextCursor || mine.loading) return;
    setOlder({ ...mine, key: agentId, loading: true, failed: false });
    listAgentEventPage(agentId, nextCursor, pageSize).then(
      (next) =>
        setOlder((prev) => {
          if (prev.key !== agentId) return prev;
          const seen = new Set(prev.events.map((e) => e.id));
          return {
            key: agentId,
            events: [...prev.events, ...next.events.filter((e) => !seen.has(e.id))],
            nextCursor: next.nextCursor,
            loading: false,
            failed: false,
          };
        }),
      () => setOlder((prev) => (prev.key === agentId ? { ...prev, loading: false, failed: true } : prev)),
    );
  }, [agentId, mine, nextCursor, pageSize]);

  const reload = useCallback(() => {
    setOlder({ key: agentId, events: [], nextCursor: null, loading: false, failed: false });
    first.reload();
  }, [agentId, first]);

  return {
    events,
    nextCursor,
    loading: first.loading || mine.loading,
    failed: first.failed || mine.failed,
    loadOlder,
    reload,
  };
}

/** v0.0.30 🍊 The model calls of one trace (metadata only; payloads are fetched per call on demand). */
export function useTraceLlmCalls(traceId: string): { calls: LlmCall[]; loading: boolean } {
  const fetched = useAsync(() => listTraceLlmCalls(traceId), traceId);
  return { calls: fetched.data ?? [], loading: fetched.loading };
}

/**
 * v0.0.30 🍊 The recorded model calls behind one event: the exact call when the event names it, otherwise
 * every call the same agent's same module made inside this trace.
 */
export function llmCallsOf(event: ModuleEvent, calls: readonly LlmCall[]): LlmCall[] {
  const named = typeof event.detail?.llmCallId === 'string' ? event.detail.llmCallId : null;
  if (named) {
    const exact = calls.filter((c) => c.id === named);
    if (exact.length > 0) return exact;
  }
  return calls.filter((c) => c.agentId === event.agentId && c.module === event.module);
}

/** v0.0.4 🍊 Security incidents with their agents resolved, newest first. */
export function useIncidentViews(): { incident: Incident; agent: PersonRef | null }[] {
  const incidents = useTraceStore((s) => s.incidents);
  const person = usePeople();
  return useMemo(() => incidents.map((incident) => ({ incident, agent: person(incident.agentId) })), [incidents, person]);
}

/** v0.0.4 🍊 The error log with agents resolved, newest first. */
export function useErrorViews(): { entry: ErrorEntry; agent: PersonRef | null }[] {
  const errors = useTraceStore((s) => s.errors);
  const person = usePeople();
  return useMemo(() => errors.map((entry) => ({ entry, agent: person(entry.error.agentId) })), [errors, person]);
}
