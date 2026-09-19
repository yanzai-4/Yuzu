import type { Snapshot } from '../api/types';
import { parseNaturalTime } from '../lib/time';
import { CHAT_FIRST_INDEX, useChatStore } from './chat';
import { assignSeats, useRoomStore } from './room';
import { useSettingsStore } from './settings';
import { newestFirst, useSimStore } from './sim';
import { useTasksStore } from './tasks';
import { LOG_CAP, useTraceStore } from './trace';
import { useUsageStore } from './usage';

/** Builds an id → record map. */
function byKey<T>(items: readonly T[] | null | undefined, key: (item: T) => string): Record<string, T> {
  const out: Record<string, T> = {};
  for (const item of items ?? []) out[key(item)] = item;
  return out;
}

/**
 * v0.0.4 🍊 Replaces every domain store with a bootstrap snapshot (first load and after `resync`).
 * Live-only data that the snapshot does not carry (trace events, the error log) is kept.
 */
export function applySnapshot(snapshot: Snapshot): void {
  const previousRoom = useRoomStore.getState();
  const agents = byKey(snapshot.agents, (a) => a.agentId);
  // Keep retired agents we already knew so their old messages still show the right avatar.
  for (const agent of Object.values(previousRoom.agents)) {
    if (!agents[agent.agentId]) agents[agent.agentId] = { ...agent, state: 'RETIRED' };
  }
  const activeIds = (snapshot.agents ?? []).filter((a) => a.state !== 'RETIRED').map((a) => a.agentId);
  useRoomStore.setState({
    roomId: snapshot.roomId,
    roomName: snapshot.roomName,
    users: byKey(snapshot.users, (u) => u.id),
    agents,
    seats: assignSeats(previousRoom.roomId === snapshot.roomId ? previousRoom.seats : [], activeIds),
    statuses: byKey(snapshot.statuses, (s) => s.agentId),
  });

  const messages = [...(snapshot.messages ?? [])].sort((a, b) => a.seq - b.seq);
  useChatStore.setState({
    byId: byKey(messages, (m) => m.id),
    order: messages.map((m) => m.id),
    cards: byKey(snapshot.cards, (c) => c.id),
    typing: {},
    firstItemIndex: CHAT_FIRST_INDEX,
    hasOlder: messages.length > 0,
    loadingOlder: false,
  });

  useTasksStore.setState({
    lists: byKey(
      (snapshot.taskLists ?? []).map((v) => ({ ...v, current: v.current ?? null, recentArchived: v.recentArchived ?? [] })),
      (v) => v.agentId,
    ),
    tickets: byKey(snapshot.tickets, (t) => t.id),
  });

  useUsageStore.setState({ usage: snapshot.usage ?? null });

  useSimStore.setState({
    emails: byKey(snapshot.emails, (e) => e.id),
    emailOrder: newestFirst(snapshot.emails ?? []),
    trades: byKey(snapshot.trades, (t) => t.id),
    tradeOrder: newestFirst(snapshot.trades ?? []),
    portfolios: byKey(snapshot.portfolios, (p) => p.agentId),
  });

  const incidents = [...(snapshot.incidents ?? [])]
    .map((incident, index) => ({ incident, t: parseNaturalTime(incident.time), index }))
    .sort((a, b) => (Number.isNaN(a.t) || Number.isNaN(b.t) ? 0 : b.t - a.t) || b.index - a.index)
    .map((entry) => entry.incident)
    .slice(0, LOG_CAP);
  useTraceStore.setState({ incidents });

  useSettingsStore.setState({ settings: snapshot.settings ?? null });
}
