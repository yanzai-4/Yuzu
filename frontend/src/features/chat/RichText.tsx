import clsx from 'clsx';
import { memo, useMemo, type CSSProperties, type ReactNode } from 'react';
import { splitMentions, type MentionTarget } from '../../lib/mentions';
import { useMentionTargets } from '../../stores/selectors';
import { useSessionStore } from '../../stores/session';
import { parseBlocks, URL_PATTERN } from './markdown';

interface InlineContext {
  matcher: RegExp | null;
  targets: MentionTarget[];
  selfId: string | null;
}

/** v0.0.4 🍊 Message text with light Markdown, clickable links and highlighted @mentions. */
export const RichText = memo(function RichText({
  text,
  className,
  trailing,
}: {
  text: string;
  className?: string;
  /** Inline node appended to the last paragraph (streaming caret). */
  trailing?: ReactNode;
}) {
  const { matcher, targets } = useMentionTargets();
  const selfId = useSessionStore((s) => s.user?.id ?? null);
  const blocks = useMemo(() => parseBlocks(text), [text]);
  const ctx: InlineContext = { matcher, targets, selfId };
  const last = blocks[blocks.length - 1];
  const trailingInside = last?.type === 'paragraph';
  return (
    <div className={clsx('space-y-1.5 text-sm leading-relaxed break-words', className)}>
      {blocks.map((block, i) => {
        switch (block.type) {
          case 'heading':
            return (
              <p key={i} className={clsx('font-bold', block.level === 1 ? 'text-base' : 'text-sm')}>
                {inline(block.text, ctx, `h${i}`)}
              </p>
            );
          case 'list': {
            const List = block.ordered ? 'ol' : 'ul';
            return (
              <List key={i} className={clsx('space-y-0.5 pl-5', block.ordered ? 'list-decimal' : 'list-disc marker:text-accent')}>
                {block.items.map((item, j) => (
                  <li key={j}>{inline(item, ctx, `l${i}-${j}`)}</li>
                ))}
              </List>
            );
          }
          case 'code':
            return (
              <pre key={i} className="overflow-x-auto rounded-lg bg-surface-3 px-2.5 py-2 font-mono text-xs">
                {block.text}
              </pre>
            );
          default:
            return (
              <p key={i} className="whitespace-pre-wrap">
                {inline(block.text, ctx, `p${i}`)}
                {trailingInside && i === blocks.length - 1 ? trailing : null}
              </p>
            );
        }
      })}
      {trailing && !trailingInside ? <p>{trailing}</p> : null}
    </div>
  );
});

/** Code spans → bold → mentions and links. */
function inline(text: string, ctx: InlineContext, key: string): ReactNode[] {
  return text.split(/(`[^`\n]+`)/g).flatMap<ReactNode>((part, i) => {
    if (part.length > 2 && part.startsWith('`') && part.endsWith('`')) {
      return [
        <code key={`${key}c${i}`} className="rounded bg-surface-3 px-1 py-px font-mono text-[0.85em]">
          {part.slice(1, -1)}
        </code>,
      ];
    }
    return part.split(/(\*\*[^*\n]+\*\*)/g).flatMap<ReactNode>((seg, j) =>
      seg.length > 4 && seg.startsWith('**') && seg.endsWith('**')
        ? [<strong key={`${key}b${i}-${j}`}>{mentionsAndLinks(seg.slice(2, -2), ctx, `${key}b${i}-${j}`)}</strong>]
        : mentionsAndLinks(seg, ctx, `${key}t${i}-${j}`),
    );
  });
}

function mentionsAndLinks(text: string, ctx: InlineContext, key: string): ReactNode[] {
  return splitMentions(text, ctx.matcher, ctx.targets).flatMap<ReactNode>((segment, i) => {
    const target = segment.target;
    if (target) {
      const self = target.kind === 'human' && target.id === ctx.selfId;
      return [
        <span
          key={`${key}m${i}`}
          title={target.kind === 'all' ? 'Everyone' : `${target.name}${target.subtitle ? ` · ${target.subtitle}` : ''}`}
          style={target.color && !self ? ({ '--hue': target.color } as CSSProperties) : undefined}
          className={clsx(
            'rounded px-0.5 font-semibold',
            self || target.kind === 'all' ? 'bg-accent-soft text-accent-soft-ink' : 'hue-soft text-ink',
          )}
        >
          {segment.text}
        </span>,
      ];
    }
    return segment.text.split(URL_PATTERN).map((piece, j) =>
      j % 2 === 1 ? (
        <a
          key={`${key}u${i}-${j}`}
          href={piece}
          target="_blank"
          rel="noreferrer noopener"
          className="font-medium text-info underline decoration-info/40 underline-offset-2 hover:decoration-info"
        >
          {piece}
        </a>
      ) : (
        piece
      ),
    );
  });
}
