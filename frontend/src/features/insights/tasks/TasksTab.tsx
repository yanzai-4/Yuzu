import { useState } from 'react';
import { EmptyState } from '../../../components/EmptyState';
import { Tabs } from '../../../components/Tabs';
import { approveList } from './taskActions';
import { TaskListCard } from './TaskListCard';
import { TicketBoard } from './TicketBoard';
import { useTaskCards, useTicketColumns } from './useTasksData';

/** v0.0.4 🍊 Tasks tab (container): task lists (approvals first) and the ticket board, fed by selectors. */
export function TasksTab() {
  const { cards, awaiting } = useTaskCards();
  const board = useTicketColumns();
  const [view, setView] = useState<'lists' | 'board'>('lists');

  return (
    <div className="space-y-3 p-3">
      <Tabs
        label="Tasks views"
        value={view}
        onChange={setView}
        stretch
        tabs={[
          { id: 'lists', label: 'Task lists', icon: 'list', count: awaiting, alert: awaiting > 0 },
          { id: 'board', label: 'Tickets', icon: 'layers', count: board.total },
        ]}
      />
      {view === 'board' ? (
        <TicketBoard columns={board.columns} cancelled={board.cancelled} total={board.total} />
      ) : cards.length === 0 ? (
        <EmptyState icon="list" title="No coworkers, no task lists">
          Hire a coworker to see its task list here.
        </EmptyState>
      ) : (
        <div className="space-y-2.5">
          {awaiting > 0 ? (
            <p className="rounded-lg bg-warn-bg px-2.5 py-1.5 text-xs font-semibold text-warn-ink">
              {awaiting === 1 ? '1 finished list is' : `${awaiting} finished lists are`} waiting for your approval.
            </p>
          ) : null}
          {cards.map((card) => (
            <TaskListCard key={card.view.agentId} card={card} onApprove={approveList} />
          ))}
        </div>
      )}
    </div>
  );
}
