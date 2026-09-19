import type { ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

/** v0.0.4 🍊 Friendly placeholder for empty lists. */
export function EmptyState({ icon, title, children }: { icon: IconName; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-line-strong px-4 py-8 text-center">
      <span className="grid size-9 place-items-center rounded-full bg-surface-3 text-ink-3">
        <Icon name={icon} size={18} />
      </span>
      <p className="text-sm font-semibold text-ink-2">{title}</p>
      {children ? <div className="max-w-xs text-xs text-ink-3">{children}</div> : null}
    </div>
  );
}
