import type { Agent, AgentStatus, DeskState } from '../../api/types';

/**
 * v0.0.4 🍊 Office view model — the only contract between the office data and its visuals.
 *
 * Views are flattened to primitives so memoized desk components re-render only when their own
 * values change. A redesigned office only needs `useOfficeData()` and these types.
 */

/** v0.0.4 🍊 An occupied desk. */
export interface AgentDeskView {
  kind: 'agent';
  /** Desk index 0..7 (4 × 2 grid, row-major). */
  seat: number;
  agentId: string;
  name: string;
  title: string;
  avatarKey: string;
  color: string;
  /** Agent lifecycle is PAUSED (the desk state is then PAUSED too). */
  paused: boolean;
  /** Desk state driving the animation (IDLE, WORKING, THINKING, TALKING, WAITING, PAUSED, ERROR). */
  state: DeskState;
  /** `bubble.module` (free text; usually a ModuleKind). */
  module: string;
  /** `bubble.summary` (may be empty). */
  summary: string;
  poolSize: number;
  pendingBatches: number;
}

/** v0.0.4 🍊 A free desk ("Hire a coworker"). */
export interface EmptyDeskView {
  kind: 'empty';
  seat: number;
}

/** v0.0.4 🍊 One of the eight desks. */
export type DeskView = AgentDeskView | EmptyDeskView;

/** v0.0.4 🍊 Effective desk state: a paused agent is PAUSED whatever its last status said. */
export function deskStateOf(agent: Agent, status: AgentStatus | undefined): DeskState {
  return agent.state === 'PAUSED' ? 'PAUSED' : (status?.state ?? 'IDLE');
}

/** v0.0.4 🍊 Builds the eight desk views from seats, agents and live statuses. */
export function toDeskViews(
  seats: readonly (string | null)[],
  agents: Record<string, Agent>,
  statuses: Record<string, AgentStatus>,
): DeskView[] {
  return seats.map((agentId, seat): DeskView => {
    const agent = agentId ? agents[agentId] : undefined;
    if (!agent || agent.state === 'RETIRED') return { kind: 'empty', seat };
    const status = statuses[agent.agentId];
    return {
      kind: 'agent',
      seat,
      agentId: agent.agentId,
      name: agent.name,
      title: agent.title,
      avatarKey: agent.avatarKey,
      color: agent.color,
      paused: agent.state === 'PAUSED',
      state: deskStateOf(agent, status),
      module: status?.bubble?.module ?? '',
      summary: status?.bubble?.summary ?? '',
      poolSize: status?.poolSize ?? 0,
      pendingBatches: status?.pendingBatches ?? 0,
    };
  });
}

/** v0.0.4 🍊 How many desks are in each state (most common first). */
export function countDeskStates(desks: readonly DeskView[]): [DeskState, number][] {
  const counts = new Map<DeskState, number>();
  for (const desk of desks) if (desk.kind === 'agent') counts.set(desk.state, (counts.get(desk.state) ?? 0) + 1);
  return [...counts.entries()].sort((a, b) => b[1] - a[1]);
}
