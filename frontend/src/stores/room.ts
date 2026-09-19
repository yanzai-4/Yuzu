import { create } from 'zustand';
import type { Agent, AgentStatus, User } from '../api/types';
import { CowList, CowRecord } from './cow';

/** v0.0.4 🍊 The room every user joins. */
export const DEFAULT_ROOM_ID = 'room-0001';
/** v0.0.4 🍊 Desks on the office floor (the backend allows at most 8 agents). */
export const DESK_COUNT = 8;

/** v0.0.4 🍊 Shape of the room store. */
export interface RoomState {
  roomId: string;
  roomName: string;
  users: Record<string, User>;
  /** Every known agent, including retired ones (kept so old chat messages still resolve). */
  agents: Record<string, Agent>;
  /** Desk → agentId; seats stay stable while agents come and go. */
  seats: (string | null)[];
  statuses: Record<string, AgentStatus>;
}

/** v0.0.4 🍊 Room, humans, agents, desk seats and live agent statuses. */
export const useRoomStore = create<RoomState>()(() => ({
  roomId: DEFAULT_ROOM_ID,
  roomName: '',
  users: {},
  agents: {},
  seats: Array<string | null>(DESK_COUNT).fill(null),
  statuses: {},
}));

/** v0.0.4 🍊 Seats agents at desks, keeping previous desks where possible. */
export function assignSeats(previous: readonly (string | null)[], agentIds: readonly string[]): (string | null)[] {
  const wanted = new Set(agentIds);
  const seats = Array.from({ length: DESK_COUNT }, (_, i) => {
    const id = previous[i] ?? null;
    return id && wanted.has(id) ? id : null;
  });
  for (const id of agentIds) {
    if (seats.includes(id)) continue;
    const free = seats.indexOf(null);
    if (free < 0) break;
    seats[free] = id;
  }
  return seats;
}

/** v0.0.4 🍊 Batched, copy-on-write writer of the room store used by the event reducer. */
export class RoomDraft {
  private readonly users: CowRecord<User>;
  private readonly agents: CowRecord<Agent>;
  private readonly statuses: CowRecord<AgentStatus>;
  private readonly seats: CowList<string | null>;

  /** v0.0.4 🍊 Starts a draft from the current store state. */
  constructor(state: RoomState = useRoomStore.getState()) {
    this.users = new CowRecord(state.users);
    this.agents = new CowRecord(state.agents);
    this.statuses = new CowRecord(state.statuses);
    this.seats = new CowList(state.seats);
  }

  /** v0.0.4 🍊 Adds or updates a human. */
  upsertUser(user: User): void {
    this.users.set(user.id, user);
  }

  /** v0.0.4 🍊 Adds or updates an agent and gives it a desk (retired agents leave their desk). */
  upsertAgent(agent: Agent): void {
    this.agents.set(agent.agentId, agent);
    if (agent.state === 'RETIRED') this.unseat(agent.agentId);
    else this.seat(agent.agentId);
  }

  /** v0.0.4 🍊 Marks an agent retired, frees its desk and drops its live status. */
  removeAgent(agentId: string): void {
    const agent = this.agents.get(agentId);
    if (agent && agent.state !== 'RETIRED') this.agents.set(agentId, { ...agent, state: 'RETIRED' });
    this.unseat(agentId);
    this.statuses.delete(agentId);
  }

  /** v0.0.4 🍊 Replaces an agent's live desk status. */
  upsertStatus(status: AgentStatus): void {
    this.statuses.set(status.agentId, status);
  }

  /** v0.0.4 🍊 Writes every changed collection to the store in one update. */
  commit(): void {
    const patch: Partial<RoomState> = {};
    if (this.users.changed) patch.users = this.users.value;
    if (this.agents.changed) patch.agents = this.agents.value;
    if (this.statuses.changed) patch.statuses = this.statuses.value;
    if (this.seats.changed) patch.seats = this.seats.value;
    if (Object.keys(patch).length > 0) useRoomStore.setState(patch);
  }

  private seat(agentId: string): void {
    if (this.seats.items.includes(agentId)) return;
    const free = this.seats.items.indexOf(null);
    if (free >= 0) this.seats.mutable()[free] = agentId;
  }

  private unseat(agentId: string): void {
    const index = this.seats.items.indexOf(agentId);
    if (index >= 0) this.seats.mutable()[index] = null;
  }
}
