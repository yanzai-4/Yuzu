import clsx from 'clsx';
import type { ReactNode } from 'react';

/** v0.0.4 🍊 Shared classes of text inputs, selects and textareas. */
export const inputClass =
  'w-full rounded-lg border border-line-strong bg-surface px-2.5 py-1.5 text-sm text-ink placeholder:text-ink-3 shadow-sm outline-none transition focus:border-accent focus:ring-2 focus:ring-accent/25 disabled:opacity-60';

/** v0.0.4 🍊 A labelled form field with an optional hint or error below. */
export function Field({
  label,
  htmlFor,
  hint,
  error,
  children,
  className,
}: {
  label: ReactNode;
  htmlFor?: string;
  hint?: ReactNode;
  error?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={clsx('flex flex-col gap-1', className)}>
      <label htmlFor={htmlFor} className="text-xs font-semibold text-ink-2">
        {label}
      </label>
      {children}
      {error ? (
        <p className="text-xs text-danger-ink">{error}</p>
      ) : hint ? (
        <p className="text-xs text-ink-3">{hint}</p>
      ) : null}
    </div>
  );
}

/** v0.0.4 🍊 Uppercase section heading used inside panels and drawers. */
export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-2">
      <h3 className="text-[11px] font-bold tracking-wider text-ink-3 uppercase">{children}</h3>
      {action}
    </div>
  );
}
