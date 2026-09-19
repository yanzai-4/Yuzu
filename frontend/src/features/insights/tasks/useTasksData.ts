import { useMemo } from 'react';
import { useActiveAgents, usePeople } from '../../../stores/selectors';
import { useTasksStore } from '../../../stores/tasks';
import { buildTaskCards, buildTicketColumns, type TaskCardData } from './tasksData';

/** v0.0.4 🍊 Task-list cards of every seated agent (approvals first) and how many await approval. */
export function useTaskCards(): { cards: TaskCardData[]; awaiting: number } {
  const lists = useTasksStore((s) => s.lists);
  const agents = useActiveAgents();
  const person = usePeople();
  return useMemo(() => {
    const cards = buildTaskCards(agents, lists, person);
    return { cards, awaiting: cards.filter((c) => c.view.current?.status === 'AWAITING_APPROVAL').length };
  }, [agents, lists, person]);
}

/** v0.0.4 🍊 The ticket board columns. */
export function useTicketColumns(): ReturnType<typeof buildTicketColumns> {
  const tickets = useTasksStore((s) => s.tickets);
  const person = usePeople();
  return useMemo(() => buildTicketColumns(tickets, person), [tickets, person]);
}

/** v0.0.4 🍊 The task-list card of one agent (Agent Inspector), or null before it is known. */
export function useAgentTaskCard(agentId: string): TaskCardData | null {
  const view = useTasksStore((s) => s.lists[agentId]);
  const person = usePeople();
  return useMemo(() => {
    const agent = person(agentId);
    return view && agent ? { view, agent } : null;
  }, [view, person, agentId]);
}
