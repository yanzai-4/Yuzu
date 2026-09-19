import clsx from 'clsx';
import { memo, useState, type FormEvent } from 'react';
import type { Card } from '../../api/types';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { inputClass } from '../../components/Field';
import { Icon } from '../../components/Icon';
import { clockTime } from '../../lib/time';
import { useChatStore } from '../../stores/chat';
import { answerChatCard } from './chatActions';

const POSITIVE = /^(approve|approved|yes|allow|accept|confirm|go ahead|ok)\b/i;
const NEGATIVE = /^(reject|rejected|deny|no|decline|cancel|block)\b/i;

const STATUS_BADGE: Record<Card['status'], { tone: 'accent' | 'leaf' | 'neutral' | 'danger'; label: string }> = {
  OPEN: { tone: 'accent', label: 'Needs an answer' },
  ANSWERED: { tone: 'leaf', label: 'Answered' },
  CANCELLED: { tone: 'neutral', label: 'Cancelled' },
  EXPIRED: { tone: 'danger', label: 'Expired' },
};

/** v0.0.4 🍊 Question / approval card: options, optional free-text "Other", and its answered state. */
export const CardView = memo(function CardView({ cardId }: { cardId: string }) {
  const card = useChatStore((s) => s.cards[cardId]);
  const [pending, setPending] = useState<string | null>(null);
  const [other, setOther] = useState('');

  if (!card) {
    return <div className="mt-1.5 rounded-xl border border-dashed border-line p-3 text-xs text-ink-3">Loading card…</div>;
  }

  const open = card.status === 'OPEN';
  const chosen = new Set(card.answer?.optionIds ?? []);
  const approval = card.kind === 'APPROVAL';
  const badge = STATUS_BADGE[card.status];

  const answer = async (key: string, optionIds: string[], otherText?: string) => {
    setPending(key);
    const ok = await answerChatCard(card.id, optionIds, otherText);
    setPending(null);
    if (ok) setOther('');
  };

  const submitOther = (event: FormEvent) => {
    event.preventDefault();
    if (other.trim()) void answer('other', [], other.trim());
  };

  return (
    <div
      className={clsx(
        'mt-1.5 max-w-xl overflow-hidden rounded-xl border shadow-sm',
        open ? 'border-accent/40 bg-surface' : 'border-line bg-surface-2',
      )}
    >
      <div className={clsx('flex items-center gap-2 px-3 py-2', open ? 'bg-accent-soft' : 'bg-surface-3')}>
        <Icon name={approval ? 'shield' : 'message'} size={15} className={open ? 'text-accent-soft-ink' : 'text-ink-3'} />
        <span className={clsx('text-xs font-bold', open ? 'text-accent-soft-ink' : 'text-ink-2')}>
          {approval ? 'Approval request' : 'Question'}
        </span>
        <span className="flex-1" />
        <Badge tone={badge.tone}>{badge.label}</Badge>
      </div>
      <div className="space-y-2.5 p-3">
        <p className="text-sm font-medium">{card.prompt}</p>
        {card.options.length > 0 ? (
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Answer options">
            {card.options.map((option) => {
              const selected = chosen.has(option.id);
              const tone = approval && POSITIVE.test(option.label) ? 'leaf' : approval && NEGATIVE.test(option.label) ? 'danger' : 'secondary';
              return (
                <Button
                  key={option.id}
                  size="sm"
                  variant={open ? tone : 'secondary'}
                  icon={selected ? 'check' : undefined}
                  loading={pending === option.id}
                  disabled={!open || pending !== null}
                  aria-pressed={selected}
                  onClick={() => void answer(option.id, [option.id])}
                  className={clsx(!open && selected && 'border-leaf bg-leaf-soft text-leaf-ink opacity-100', !open && !selected && 'opacity-45')}
                >
                  {option.label}
                </Button>
              );
            })}
          </div>
        ) : null}
        {open && card.allowOther ? (
          <form onSubmit={submitOther} className="flex gap-1.5">
            <input
              value={other}
              onChange={(e) => setOther(e.target.value)}
              placeholder="Other: write your own answer…"
              aria-label="Other answer"
              maxLength={1000}
              disabled={pending !== null}
              className={clsx(inputClass, 'h-8 py-1 text-xs')}
            />
            <Button type="submit" size="sm" variant="primary" loading={pending === 'other'} disabled={!other.trim() || pending !== null}>
              Send
            </Button>
          </form>
        ) : null}
        {card.status === 'ANSWERED' ? (
          <p className="flex flex-wrap items-center gap-1 text-xs text-ink-2">
            <Icon name="check" size={13} className="text-leaf" />
            Answered by <strong>{card.answeredByName ?? 'someone'}</strong>
            {card.answeredTime ? <span className="text-ink-3">at {clockTime(card.answeredTime)}</span> : null}
            {card.answer?.otherText ? <q className="basis-full pl-4 text-ink italic">{card.answer.otherText}</q> : null}
          </p>
        ) : null}
        {card.status === 'CANCELLED' || card.status === 'EXPIRED' ? (
          <p className="text-xs text-ink-3">
            {card.status === 'EXPIRED' ? 'Nobody answered in time; the agent moved on.' : 'The agent withdrew this question.'}
          </p>
        ) : null}
      </div>
    </div>
  );
});
