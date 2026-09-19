import clsx from 'clsx';
import { UserAvatar } from '../../components/Avatars';
import { CitrusAvatar } from '../../components/citrus/CitrusAvatar';
import { Icon } from '../../components/Icon';
import type { MentionTarget } from '../../lib/mentions';

/** v0.0.4 🍊 Autocomplete popup listing @-mention candidates above the composer. */
export function MentionMenu({
  id,
  candidates,
  activeIndex,
  onHover,
  onPick,
}: {
  id: string;
  candidates: MentionTarget[];
  activeIndex: number;
  onHover: (index: number) => void;
  onPick: (target: MentionTarget) => void;
}) {
  return (
    <ul
      id={id}
      role="listbox"
      aria-label="Mention suggestions"
      className="absolute inset-x-0 bottom-full z-10 mb-2 max-h-64 overflow-y-auto rounded-xl border border-line bg-surface p-1 shadow-md"
    >
      {candidates.map((target, index) => (
        <li
          key={`${target.kind}-${target.id}`}
          id={`${id}-${index}`}
          role="option"
          aria-selected={index === activeIndex}
          onMouseEnter={() => onHover(index)}
          // mousedown keeps the textarea focused (click would blur it first).
          onMouseDown={(event) => {
            event.preventDefault();
            onPick(target);
          }}
          className={clsx(
            'flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5',
            index === activeIndex ? 'bg-accent-soft' : 'hover:bg-surface-2',
          )}
        >
          {target.kind === 'agent' ? (
            <CitrusAvatar avatarKey={target.avatarKey ?? ''} color={target.color ?? ''} size={24} />
          ) : target.kind === 'human' ? (
            <UserAvatar name={target.name} color={target.color} size={22} />
          ) : (
            <span className="grid size-6 place-items-center rounded-full bg-accent text-accent-ink">
              <Icon name="users" size={13} />
            </span>
          )}
          <span className="text-sm font-semibold">@{target.name}</span>
          <span className="truncate text-xs text-ink-3">{target.subtitle}</span>
        </li>
      ))}
    </ul>
  );
}
