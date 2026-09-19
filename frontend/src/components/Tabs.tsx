import clsx from 'clsx';
import type { KeyboardEvent, ReactNode } from 'react';
import { Icon, type IconName } from './Icon';

/** v0.0.4 🍊 One tab of a Tabs bar. */
export interface TabItem<T extends string> {
  id: T;
  label: ReactNode;
  icon?: IconName;
  /** Small counter bubble (hidden when 0 or undefined). */
  count?: number;
  /** Emphasize the counter (e.g. errors). */
  alert?: boolean;
}

/** v0.0.4 🍊 Accessible segmented tab bar (arrow keys move between tabs). */
export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
  label,
  stretch = false,
  className,
}: {
  tabs: TabItem<T>[];
  value: T;
  onChange: (id: T) => void;
  label: string;
  stretch?: boolean;
  className?: string;
}) {
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    const index = tabs.findIndex((t) => t.id === value);
    const next = tabs[(index + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
    if (next) {
      event.preventDefault();
      onChange(next.id);
      event.currentTarget.querySelector<HTMLButtonElement>(`[data-tab="${next.id}"]`)?.focus();
    }
  };
  return (
    <div
      role="tablist"
      aria-label={label}
      onKeyDown={onKeyDown}
      className={clsx('flex gap-1 rounded-xl bg-surface-3 p-1', stretch && 'w-full', className)}
    >
      {tabs.map((tab) => {
        const selected = tab.id === value;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            data-tab={tab.id}
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab.id)}
            className={clsx(
              'inline-flex h-7 items-center justify-center gap-1.5 rounded-lg px-2.5 text-xs font-semibold whitespace-nowrap transition-colors',
              stretch && 'flex-1',
              selected ? 'bg-surface text-ink shadow-sm' : 'text-ink-3 hover:text-ink',
            )}
          >
            {tab.icon ? <Icon name={tab.icon} size={14} /> : null}
            {tab.label}
            {tab.count ? (
              <span
                className={clsx(
                  'min-w-4 rounded-full px-1 text-[10px] leading-4',
                  tab.alert ? 'bg-danger text-white' : 'bg-line-strong/60 text-ink-2',
                )}
              >
                {tab.count > 99 ? '99+' : tab.count}
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
}
