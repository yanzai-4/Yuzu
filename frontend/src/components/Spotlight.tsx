import clsx from 'clsx';
import type { ReactNode } from 'react';
import type { SpotlightId } from '../features/demo/steps';
import { useSpotlit } from '../stores/demo';

/**
 * v0.0.33 🍊 Highlights its subtree while the guided tour points at `id`. It only adds a ring and a
 * glow: nothing else is dimmed, because dimming would hide that all three panes are alive at once.
 */
export function Spotlight({ id, className, children }: { id: SpotlightId; className?: string; children: ReactNode }) {
  const lit = useSpotlit(id);
  return (
    <div
      data-spotlight={id}
      data-lit={lit || undefined}
      className={clsx(
        'relative flex min-h-0 min-w-0 flex-col rounded-2xl transition-shadow duration-300',
        lit && 'ring-2 ring-accent shadow-[0_0_0_6px_var(--color-accent-soft)]',
        className,
      )}
    >
      {children}
    </div>
  );
}
