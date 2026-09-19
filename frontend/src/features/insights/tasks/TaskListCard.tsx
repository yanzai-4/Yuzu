import clsx from 'clsx';
import { memo, useState } from 'react';
import type { TaskItem, TaskList } from '../../../api/types';
import { PersonAvatar } from '../../../components/Avatars';
import { Badge, type BadgeTone } from '../../../components/Badge';
import { Button } from '../../../components/Button';
import { Collapsible } from '../../../components/Collapsible';
import { Icon } from '../../../components/Icon';
import { clockTime } from '../../../lib/time';
import type { TaskCardData } from './tasksData';

const STATUS: Record<TaskList['status'], { label: string; tone: BadgeTone }> = {
  ACTIVE: { label: 'In progress', tone: 'info' },
  AWAITING_APPROVAL: { label: 'Awaiting approval', tone: 'warn' },
  ARCHIVED: { label: 'Archived', tone: 'neutral' },
};

/** v0.0.4 🍊 One task item: DONE checked, DOING highlighted, STRUCK crossed out with its reason. */
function TaskItemRow({ item }: { item: TaskItem }) {
  const reason = item.struckReason ?? 'Struck';
  return (
    <li
      className={clsx(
        'flex items-start gap-2 rounded-lg px-2 py-1 text-[13px]',
        item.state === 'DOING' && 'bg-accent-soft font-semibold text-accent-soft-ink',
      )}
      title={item.state === 'STRUCK' ? `Struck: ${reason}` : undefined}
    >
      <span className="mt-0.5 shrink-0" aria-hidden="true">
        {item.state === 'DONE' ? (
          <span className="grid size-4 place-items-center rounded-full bg-leaf text-white dark:text-[#10200a]">
            <Icon name="check" size={11} strokeWidth={3} />
          </span>
        ) : item.state === 'DOING' ? (
          <span className="relative grid size-4 place-items-center">
            <span className="status-ping absolute size-2.5 rounded-full bg-accent" />
            <span className="size-2.5 rounded-full bg-accent" />
          </span>
        ) : item.state === 'STRUCK' ? (
          <span className="grid size-4 place-items-center rounded-full bg-surface-3 text-ink-3">
            <Icon name="x" size={11} strokeWidth={3} />
          </span>
        ) : (
          <span className="block size-4 rounded-full border-2 border-line-strong" />
        )}
      </span>
      <span className="min-w-0 flex-1">
        <span className={clsx(item.state === 'DONE' && 'text-ink-3', item.state === 'STRUCK' && 'text-ink-3 line-through')}>
          {item.text}
        </span>
        <span className="sr-only">{` (${item.state.toLowerCase()})`}</span>
        {item.state === 'STRUCK' ? (
          <span className="mt-0.5 flex items-center gap-1 text-[11px] text-ink-3 no-underline">
            <Icon name="info" size={11} /> {reason}
          </span>
        ) : null}
        {item.note ? <span className="mt-0.5 block text-[11px] font-normal text-ink-3">{item.note}</span> : null}
      </span>
    </li>
  );
}

/** v0.0.4 🍊 An agent's current task list (+ Approve & archive) and its recently archived lists (props only). */
export const TaskListCard = memo(function TaskListCard({
  card,
  showAgent = true,
  onApprove,
}: {
  card: TaskCardData;
  showAgent?: boolean;
  /** Called by "Approve & archive" (POST /api/task-lists/{listId}/approve). */
  onApprove: (listId: string, goal: string) => Promise<unknown>;
}) {
  const { view, agent } = card;
  const [approving, setApproving] = useState(false);
  const list = view.current;
  const done = list ? list.items.filter((i) => i.state === 'DONE').length : 0;
  const total = list ? list.items.filter((i) => i.state !== 'STRUCK').length : 0;

  return (
    <article className="rounded-xl border border-line bg-surface p-3 shadow-sm">
      <header className="flex items-start gap-2">
        {showAgent ? <PersonAvatar person={agent} size={30} /> : null}
        <div className="min-w-0 flex-1">
          {showAgent ? <p className="text-[11px] font-semibold text-ink-3">{agent.name}</p> : null}
          <h4 className="text-sm leading-snug font-bold">{list ? list.goal : 'No current task list'}</h4>
          {list ? (
            <p className="text-[11px] text-ink-3">
              from {list.publisherName} · {clockTime(list.time)}
            </p>
          ) : null}
        </div>
        {list ? <Badge tone={STATUS[list.status].tone}>{STATUS[list.status].label}</Badge> : null}
      </header>
      {list ? (
        <>
          <div className="mt-2 flex items-center gap-2" aria-label={`${done} of ${total} done`}>
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-3">
              <div className="h-full rounded-full bg-leaf transition-[width] duration-500" style={{ width: `${total ? (done / total) * 100 : 0}%` }} />
            </div>
            <span className="text-[11px] font-semibold text-ink-3 tabular-nums">
              {done}/{total}
            </span>
          </div>
          <ul className="mt-2 space-y-0.5">
            {[...list.items].sort((a, b) => a.ord - b.ord).map((item) => (
              <TaskItemRow key={item.id} item={item} />
            ))}
          </ul>
          {list.outcome ? (
            <p className="mt-2 rounded-lg bg-leaf-soft px-2.5 py-1.5 text-xs text-leaf-ink">
              <strong>Outcome:</strong> {list.outcome}
            </p>
          ) : null}
          {list.status === 'AWAITING_APPROVAL' ? (
            <Button
              size="sm"
              variant="leaf"
              icon="check"
              className="mt-2.5 w-full"
              loading={approving}
              onClick={async () => {
                setApproving(true);
                await onApprove(list.id, list.goal);
                setApproving(false);
              }}
            >
              Approve &amp; archive
            </Button>
          ) : null}
        </>
      ) : (
        <p className="mt-1 text-xs text-ink-3">Waiting for new work.</p>
      )}
      {view.recentArchived.length > 0 ? (
        <Collapsible className="mt-2 border-t border-line pt-1.5" title={`Recently archived (${view.recentArchived.length})`}>
          <ul className="space-y-1.5">
            {view.recentArchived.map((archived) => (
              <li key={archived.id} className="rounded-lg bg-surface-2 px-2.5 py-1.5 text-xs">
                <p className="font-semibold">{archived.goal}</p>
                <p className="text-[11px] text-ink-3">
                  {archived.items.filter((i) => i.state === 'DONE').length}/{archived.items.length} done
                  {archived.archivedTime ? ` · archived ${clockTime(archived.archivedTime)}` : ''}
                </p>
                {archived.outcome ? <p className="mt-0.5 text-ink-2">{archived.outcome}</p> : null}
              </li>
            ))}
          </ul>
        </Collapsible>
      ) : null}
    </article>
  );
});
