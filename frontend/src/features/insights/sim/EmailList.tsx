import clsx from 'clsx';
import { memo, useState } from 'react';
import type { Email } from '../../../api/types';
import { PersonAvatar } from '../../../components/Avatars';
import { Badge } from '../../../components/Badge';
import { EmptyState } from '../../../components/EmptyState';
import { Icon } from '../../../components/Icon';
import { clockTime } from '../../../lib/time';
import type { WithAgent } from './useSimData';

const STATUS_TONE = { RECEIVED: 'info', SENT: 'leaf', BLOCKED: 'danger' } as const;

/** v0.0.4 🍊 One simulated email: direction, parties, subject and status; expands to show the body. */
const EmailRow = memo(function EmailRow({ data }: { data: WithAgent<Email> }) {
  const [open, setOpen] = useState(false);
  const { item: email, agent } = data;
  const blocked = email.status === 'BLOCKED';
  return (
    <li className={clsx('rounded-lg border', blocked ? 'border-danger/40 bg-danger-soft/40' : 'border-line bg-surface')}>
      <button type="button" aria-expanded={open} onClick={() => setOpen((o) => !o)} className="flex w-full items-start gap-2 p-2.5 text-left">
        <Badge tone={email.direction === 'IN' ? 'info' : 'accent'} className="mt-0.5">
          {email.direction}
        </Badge>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[11px] text-ink-3">
            {email.from} <span aria-hidden="true">→</span>
            <span className="sr-only">to</span> {email.to}
          </span>
          <span className={clsx('block truncate text-[13px] font-semibold', blocked && 'text-danger-ink')}>{email.subject || '(no subject)'}</span>
        </span>
        <span className="flex shrink-0 flex-col items-end gap-1">
          <Badge tone={STATUS_TONE[email.status]}>
            {blocked ? <Icon name="shield" size={10} /> : null}
            {email.status}
          </Badge>
          <time className="text-[10px] text-ink-3" title={email.time}>
            {clockTime(email.time)}
          </time>
        </span>
      </button>
      {open ? (
        <div className="border-t border-line px-2.5 py-2">
          {agent ? (
            <p className="mb-1.5 flex items-center gap-1.5 text-[11px] text-ink-3">
              <PersonAvatar person={agent} size={16} /> handled by {agent.name}
            </p>
          ) : null}
          <pre className="font-sans text-xs whitespace-pre-wrap text-ink-2">{email.body}</pre>
        </div>
      ) : null}
    </li>
  );
});

/** v0.0.4 🍊 Simulated mailbox, newest first (props only). */
export function EmailList({ emails }: { emails: WithAgent<Email>[] }) {
  if (emails.length === 0) {
    return (
      <EmptyState icon="mail" title="No emails yet">
        Agents with email permissions read and send simulated emails here.
      </EmptyState>
    );
  }
  return (
    <ul className="space-y-1.5">
      {emails.map((data) => (
        <EmailRow key={data.item.id} data={data} />
      ))}
    </ul>
  );
}
