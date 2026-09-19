import type { Agent, TaskListView, Ticket, TicketStatus } from '../../../api/types';
import { parseNaturalTime } from '../../../lib/time';
import type { PersonRef } from '../../../stores/people';

/** v0.0.4 🍊 View model of one agent's task lists (current + recently archived). */
export interface TaskCardData {
  view: TaskListView;
  agent: PersonRef;
}

/** v0.0.4 🍊 View model of one ticket with its resolved assignee. */
export interface TicketCardData {
  ticket: Ticket;
  assignee: PersonRef | null;
}

/** v0.0.4 🍊 One status column of the ticket board. */
export interface TicketColumn {
  status: TicketStatus;
  label: string;
  color: string;
  tickets: TicketCardData[];
}

/** v0.0.4 🍊 Board columns in workflow order (CANCELLED is folded separately). */
export const TICKET_COLUMNS: { status: TicketStatus; label: string; color: string }[] = [
  { status: 'OPEN', label: 'Open', color: '#94a3b8' },
  { status: 'ASSIGNED', label: 'Assigned', color: '#8b5cf6' },
  { status: 'IN_PROGRESS', label: 'In progress', color: '#f59e0b' },
  { status: 'DONE', label: 'Done', color: '#0ea5e9' },
  { status: 'APPROVED', label: 'Approved', color: '#16a34a' },
];

const LIST_ORDER: Record<string, number> = { AWAITING_APPROVAL: 0, ACTIVE: 1 };

/** v0.0.4 🍊 One card per seated agent, lists awaiting approval first. */
export function buildTaskCards(
  agents: readonly Agent[],
  lists: Record<string, TaskListView>,
  person: (id: string) => PersonRef | null,
): TaskCardData[] {
  return agents
    .map((a) => ({
      view: lists[a.agentId] ?? { agentId: a.agentId, current: null, recentArchived: [] },
      agent: person(a.agentId) ?? { id: a.agentId, name: a.name, kind: 'agent' as const },
    }))
    .sort((a, b) => (LIST_ORDER[a.view.current?.status ?? ''] ?? 2) - (LIST_ORDER[b.view.current?.status ?? ''] ?? 2));
}

/** v0.0.4 🍊 Tickets grouped by status (newest update first) plus the cancelled ones. */
export function buildTicketColumns(
  tickets: Record<string, Ticket>,
  person: (id: string | null | undefined) => PersonRef | null,
): { columns: TicketColumn[]; cancelled: TicketCardData[]; total: number } {
  const byStatus = new Map<TicketStatus, TicketCardData[]>();
  const all = Object.values(tickets);
  for (const ticket of all) {
    const list = byStatus.get(ticket.status) ?? [];
    list.push({ ticket, assignee: person(ticket.assigneeId) });
    byStatus.set(ticket.status, list);
  }
  const newest = (a: TicketCardData, b: TicketCardData) =>
    (parseNaturalTime(b.ticket.updatedTime) || 0) - (parseNaturalTime(a.ticket.updatedTime) || 0);
  return {
    columns: TICKET_COLUMNS.map((c) => ({ ...c, tickets: (byStatus.get(c.status) ?? []).sort(newest) })),
    cancelled: (byStatus.get('CANCELLED') ?? []).sort(newest),
    total: all.length,
  };
}
