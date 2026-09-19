import { create } from 'zustand';
import type { TaskListView, Ticket } from '../api/types';
import { CowRecord } from './cow';

/** v0.0.4 🍊 Shape of the tasks store. */
export interface TasksState {
  /** agentId → current task list + recently archived lists. */
  lists: Record<string, TaskListView>;
  tickets: Record<string, Ticket>;
}

/** v0.0.4 🍊 Per-agent task lists and the room's tickets. */
export const useTasksStore = create<TasksState>()(() => ({ lists: {}, tickets: {} }));

/** v0.0.4 🍊 Batched, copy-on-write writer of the tasks store used by the event reducer. */
export class TasksDraft {
  private readonly lists: CowRecord<TaskListView>;
  private readonly tickets: CowRecord<Ticket>;

  /** v0.0.4 🍊 Starts a draft from the current store state. */
  constructor(state: TasksState = useTasksStore.getState()) {
    this.lists = new CowRecord(state.lists);
    this.tickets = new CowRecord(state.tickets);
  }

  /** v0.0.4 🍊 Replaces an agent's task list view. */
  upsertList(view: TaskListView): void {
    this.lists.set(view.agentId, { ...view, current: view.current ?? null, recentArchived: view.recentArchived ?? [] });
  }

  /** v0.0.4 🍊 Inserts or replaces a ticket. */
  upsertTicket(ticket: Ticket): void {
    this.tickets.set(ticket.id, ticket);
  }

  /** v0.0.4 🍊 Drops the task lists of a retired agent. */
  removeAgent(agentId: string): void {
    this.lists.delete(agentId);
  }

  /** v0.0.4 🍊 Writes every changed collection to the store in one update. */
  commit(): void {
    const patch: Partial<TasksState> = {};
    if (this.lists.changed) patch.lists = this.lists.value;
    if (this.tickets.changed) patch.tickets = this.tickets.value;
    if (Object.keys(patch).length > 0) useTasksStore.setState(patch);
  }
}
