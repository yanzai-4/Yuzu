import type { Agent, User } from '../api/types';

/** v0.0.4 🍊 Display data of an agent or a human, resolved from an id (view-model for avatars/names). */
export interface PersonRef {
  id: string;
  name: string;
  kind: 'agent' | 'human' | 'unknown';
  /** Citrus avatar key (agents only). */
  avatarKey?: string;
  color?: string;
  retired?: boolean;
}

/** v0.0.4 🍊 Resolves an agent or user id to a PersonRef (unknown ids keep the id as name). */
export function toPersonRef(
  id: string | null | undefined,
  agents: Record<string, Agent>,
  users: Record<string, User>,
): PersonRef | null {
  if (!id) return null;
  const agent = agents[id];
  if (agent) {
    return { id, name: agent.name, kind: 'agent', avatarKey: agent.avatarKey, color: agent.color, retired: agent.state === 'RETIRED' };
  }
  const user = users[id];
  if (user) return { id, name: user.username, kind: 'human', color: user.color };
  return { id, name: id, kind: 'unknown' };
}

/**
 * v0.0.4 🍊 A resolver id → PersonRef for one version of agents/users that returns the same object
 * for the same id (memoized rows keep equal props).
 */
export function createPersonResolver(
  agents: Record<string, Agent>,
  users: Record<string, User>,
): (id: string | null | undefined) => PersonRef | null {
  const cache = new Map<string, PersonRef | null>();
  return (id) => {
    if (!id) return null;
    let ref = cache.get(id);
    if (ref === undefined) {
      ref = toPersonRef(id, agents, users);
      cache.set(id, ref);
    }
    return ref;
  };
}
