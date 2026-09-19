import clsx from 'clsx';
import { memo, type CSSProperties } from 'react';
import type { ChatMessage } from '../../api/types';
import { AuthorAvatar } from '../../components/Avatars';
import { Badge } from '../../components/Badge';
import { Icon } from '../../components/Icon';
import { fruitColor } from '../../lib/citrus';
import { clockTime, secondsBetween } from '../../lib/time';
import { useChatStore } from '../../stores/chat';
import { useRoomStore } from '../../stores/room';
import { useSessionStore } from '../../stores/session';
import { CardView } from './CardView';
import { RichText } from './RichText';

const KIND_BADGE: Partial<Record<ChatMessage['kind'], { label: string; tone: 'accent' | 'info' | 'warn' }>> = {
  REPORT: { label: 'Report', tone: 'info' },
  QUESTION_CARD: { label: 'Question', tone: 'accent' },
  APPROVAL_CARD: { label: 'Approval', tone: 'accent' },
};

/** True when two messages are close enough to share one author header. */
function continues(prev: ChatMessage | undefined, message: ChatMessage): boolean {
  if (!prev || prev.authorId !== message.authorId || prev.kind !== 'TEXT' || message.kind !== 'TEXT') return false;
  const gap = secondsBetween(prev.time, message.time);
  return gap !== null && gap < 180;
}

/** v0.0.4 🍊 One chat message, rendered by kind (text, warning, report, cards, system). */
export const MessageRow = memo(function MessageRow({ id, prevId }: { id: string; prevId: string | null }) {
  const message = useChatStore((s) => s.byId[id]);
  const prev = useChatStore((s) => (prevId ? s.byId[prevId] : undefined));
  const agentColor = useRoomStore((s) => (message?.authorKind === 'AGENT' ? s.agents[message.authorId]?.color : undefined));
  const self = useSessionStore((s) => s.user);
  if (!message) return null;

  if (message.kind === 'SYSTEM') {
    return (
      <div className="flex items-center gap-2 px-4 py-1.5 text-[11px] text-ink-3" role="note">
        <span className="h-px flex-1 bg-line" aria-hidden="true" />
        <span className="max-w-[80%] text-center">{message.content}</span>
        <span className="shrink-0" title={message.time}>
          {clockTime(message.time)}
        </span>
        <span className="h-px flex-1 bg-line" aria-hidden="true" />
      </div>
    );
  }

  const grouped = continues(prev, message);
  const mine = self !== null && message.authorId === self.id;
  const mentionsMe =
    !mine &&
    self !== null &&
    (message.mentionAll ||
      message.mentions.some((m) => m === self.id || m.toLowerCase() === self.username.toLowerCase()));
  const badge = KIND_BADGE[message.kind];
  const nameColor = message.authorKind === 'AGENT' ? fruitColor(agentColor) : undefined;

  return (
    <article
      aria-label={`${message.authorName} at ${clockTime(message.time)}`}
      className={clsx(
        'group relative px-3',
        grouped ? 'pt-0.5' : 'pt-3',
        mentionsMe && 'bg-accent-soft/50 shadow-[inset_3px_0_0_var(--accent)]',
      )}
    >
      <div className="flex gap-2.5">
        <div className="w-8 shrink-0">
          {grouped ? (
            <span className="block pt-0.5 text-right text-[10px] text-ink-3 opacity-0 group-hover:opacity-100" title={message.time}>
              {clockTime(message.time).replace(/\s?[AP]M$/, '')}
            </span>
          ) : (
            <AuthorAvatar kind={message.authorKind} id={message.authorId} name={message.authorName} size={32} />
          )}
        </div>
        <div className="min-w-0 flex-1 pb-1">
          {grouped ? null : (
            <header className="flex flex-wrap items-baseline gap-x-1.5 gap-y-0.5">
              <span
                className="text-sm font-bold"
                style={nameColor ? ({ textDecorationColor: nameColor } as CSSProperties) : undefined}
              >
                <span className={clsx(message.authorKind === 'AGENT' && 'underline decoration-2 underline-offset-4')}>
                  {message.authorName}
                </span>
              </span>
              {message.authorKind === 'AGENT' ? <span className="text-[10px] font-semibold text-ink-3 uppercase">AI</span> : null}
              {badge ? <Badge tone={badge.tone}>{badge.label}</Badge> : null}
              <time className="text-[11px] text-ink-3" title={message.time}>
                {clockTime(message.time)}
              </time>
            </header>
          )}
          <MessageBody message={message} accent={nameColor} />
        </div>
      </div>
    </article>
  );
});

function MessageBody({ message, accent }: { message: ChatMessage; accent?: string }) {
  const streaming = message.streamState === 'STREAMING';
  const tail = streaming ? (
    <span className="animate-caret ml-0.5 inline-block h-4 w-[2px] translate-y-0.5 bg-accent" aria-label="Streaming" />
  ) : message.streamState === 'STOPPED' ? (
    <span className="ml-1 text-[11px] text-ink-3 italic">(stopped)</span>
  ) : null;

  if (message.kind === 'WARNING') {
    return (
      <div
        role="note"
        aria-label="Security notice"
        className="mt-1 flex gap-2 rounded-xl border border-warn-line bg-warn-bg px-3 py-2 text-warn-ink"
      >
        <Icon name="shield" size={16} className="mt-0.5 shrink-0" />
        <div className="min-w-0">
          <p className="text-[11px] font-bold tracking-wide uppercase">Security notice</p>
          <RichText text={message.content} className="text-warn-ink" trailing={tail} />
        </div>
      </div>
    );
  }

  if (message.kind === 'REPORT') {
    return (
      <div
        className="mt-1 rounded-xl border border-line bg-surface-2 px-3 py-2 shadow-sm"
        style={{ borderLeft: `4px solid ${accent ?? 'var(--accent)'}` }}
      >
        <RichText text={message.content} trailing={tail} />
      </div>
    );
  }

  const card = message.kind === 'QUESTION_CARD' || message.kind === 'APPROVAL_CARD';
  return (
    <div className="text-ink">
      {message.content || tail ? (
        <RichText text={message.content} trailing={tail} />
      ) : card ? null : (
        <p className="text-sm text-ink-3 italic">(empty)</p>
      )}
      {card ? (
        message.cardId ? (
          <CardView cardId={message.cardId} />
        ) : (
          <p className="mt-1 text-xs text-ink-3">This card is no longer available.</p>
        )
      ) : null}
    </div>
  );
}
