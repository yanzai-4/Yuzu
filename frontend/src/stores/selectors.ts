import { useMemo } from 'react';
import type { Agent } from '../api/types';
import { buildMentionMatcher, MENTION_ALL, type MentionTarget } from '../lib/mentions';
import { createPersonResolver, type PersonRef } from './people';
import { useRoomStore } from './room';

/** v0.0.4 🍊 The 8 desks: each entry is the seated agent or null (empty desk). */
export function useSeatedAgents(): (Agent | null)[] {
  const seats = useRoomStore((s) => s.seats);
  const agents = useRoomStore((s) => s.agents);
  return useMemo(() => seats.map((id) => (id ? (agents[id] ?? null) : null)), [seats, agents]);
}

/** v0.0.4 🍊 Agents currently at a desk (active or paused), in desk order. */
export function useActiveAgents(): Agent[] {
  const seated = useSeatedAgents();
  return useMemo(() => seated.filter((a): a is Agent => a !== null), [seated]);
}

/** v0.0.4 🍊 Everyone who can be @-mentioned (agents, humans, @all) plus the highlight matcher. */
export function useMentionTargets(): { targets: MentionTarget[]; matcher: RegExp | null } {
  const agents = useRoomStore((s) => s.agents);
  const users = useRoomStore((s) => s.users);
  return useMemo(() => {
    const targets: MentionTarget[] = [MENTION_ALL];
    for (const agent of Object.values(agents)) {
      if (agent.state === 'RETIRED') continue;
      targets.push({
        kind: 'agent',
        id: agent.agentId,
        name: agent.name,
        color: agent.color,
        avatarKey: agent.avatarKey,
        subtitle: agent.title,
      });
    }
    for (const user of Object.values(users)) {
      targets.push({ kind: 'human', id: user.id, name: user.username, color: user.color, subtitle: 'Human' });
    }
    return { targets, matcher: buildMentionMatcher(targets) };
  }, [agents, users]);
}

/**
 * v0.0.4 🍊 A stable resolver id → PersonRef. It changes only when agents or users change, and returns
 * the same object for the same id meanwhile (so memoized rows keep their props equal).
 */
export function usePeople(): (id: string | null | undefined) => PersonRef | null {
  const agents = useRoomStore((s) => s.agents);
  const users = useRoomStore((s) => s.users);
  return useMemo(() => createPersonResolver(agents, users), [agents, users]);
}

/** v0.0.4 🍊 Display name of an agent or human id (falls back to the id). */
export function useDisplayName(id: string | null | undefined): string {
  const agentName = useRoomStore((s) => (id ? s.agents[id]?.name : undefined));
  const userName = useRoomStore((s) => (id ? s.users[id]?.username : undefined));
  return agentName ?? userName ?? id ?? '';
}
