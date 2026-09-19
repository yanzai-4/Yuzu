import { memo } from 'react';
import { PersonAvatar } from '../../../components/Avatars';
import { Collapsible } from '../../../components/Collapsible';
import { EmptyState } from '../../../components/EmptyState';
import { clockTime } from '../../../lib/time';
import type { TicketCardData, TicketColumn } from './tasksData';

/** v0.0.4 🍊 One ticket (props only): title, detail, assignee avatar, creator and last update. */
const TicketCard = memo(function TicketCard({ data }: { data: TicketCardData }) {
  const { ticket, assignee } = data;
  return (
    <li className="rounded-lg border border-line bg-surface p-2.5 shadow-sm">
      <p className="text-[13px] leading-snug font-semibold">{ticket.title}</p>
      {ticket.detail ? <p className="mt-0.5 line-clamp-2 text-xs text-ink-2">{ticket.detail}</p> : null}
      <div className="mt-1.5 flex items-center gap-1.5 text-[11px] text-ink-3">
        {assignee ? (
          <>
            <PersonAvatar person={assignee} size={18} />
            <span className="font-semibold text-ink-2">{assignee.name}</span>
          </>
        ) : (
          <span className="italic">Unassigned</span>
        )}
        <span className="flex-1" />
        <span title={`Created by ${ticket.creatorName}${ticket.requesterName ? ` for ${ticket.requesterName}` : ''}`}>
          by {ticket.creatorName}
        </span>
        <span aria-hidden="true">·</span>
        <time title={ticket.updatedTime}>{clockTime(ticket.updatedTime)}</time>
      </div>
    </li>
  );
});

/** v0.0.4 🍊 Ticket board (props only): status groups Open → Approved, cancelled tickets folded. */
export function TicketBoard({ columns, cancelled, total }: { columns: TicketColumn[]; cancelled: TicketCardData[]; total: number }) {
  if (total === 0) {
    return (
      <EmptyState icon="list" title="No tickets yet">
        Tickets appear when a coworker (or you, via chat) turns a request into tracked work.
      </EmptyState>
    );
  }
  return (
    <div className="space-y-3">
      {columns.map((column) => (
        <section key={column.status} aria-label={`${column.label} tickets`}>
          <h4 className="mb-1.5 flex items-center gap-1.5 text-xs font-bold text-ink-2">
            <span className="size-2 rounded-full" style={{ background: column.color }} aria-hidden="true" />
            {column.label}
            <span className="rounded-full bg-surface-3 px-1.5 text-[10px] text-ink-3">{column.tickets.length}</span>
          </h4>
          {column.tickets.length > 0 ? (
            <ul className="space-y-1.5">
              {column.tickets.map((data) => (
                <TicketCard key={data.ticket.id} data={data} />
              ))}
            </ul>
          ) : (
            <p className="rounded-lg border border-dashed border-line px-2.5 py-1.5 text-[11px] text-ink-3">Nothing here.</p>
          )}
        </section>
      ))}
      {cancelled.length > 0 ? (
        <Collapsible title={`Cancelled (${cancelled.length})`}>
          <ul className="space-y-1.5 opacity-70">
            {cancelled.map((data) => (
              <TicketCard key={data.ticket.id} data={data} />
            ))}
          </ul>
        </Collapsible>
      ) : null}
    </div>
  );
}
