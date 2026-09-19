import clsx from 'clsx';
import type { ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

/** v0.0.4 🍊 Rounded pane container with a header (title, subtitle, actions) and a flexible body. */
export function Panel({
  title,
  subtitle,
  icon,
  actions,
  children,
  className,
  bodyClassName,
  label,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  icon?: IconName;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
  /** Accessible name of the region. */
  label: string;
}) {
  return (
    <section
      aria-label={label}
      className={clsx(
        // flex-1 fills the tabbed (flex column) layout; in the desktop grid the cell already stretches.
        'flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-line bg-surface shadow-sm',
        className,
      )}
    >
      <header className="flex min-h-12 shrink-0 items-center gap-2 border-b border-line px-3.5 py-2">
        {icon ? <Icon name={icon} size={16} className="shrink-0 text-accent" /> : null}
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-sm leading-tight font-bold">{title}</h2>
          {subtitle ? <p className="truncate text-[11px] leading-tight text-ink-3">{subtitle}</p> : null}
        </div>
        {actions}
      </header>
      <div className={clsx('min-h-0 flex-1', bodyClassName)}>{children}</div>
    </section>
  );
}
