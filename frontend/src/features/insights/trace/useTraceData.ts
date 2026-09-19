import { useMemo } from 'react';
import type { Incident, ModuleEvent } from '../../../api/types';
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
