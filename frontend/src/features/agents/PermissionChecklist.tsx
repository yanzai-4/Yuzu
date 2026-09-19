import clsx from 'clsx';
import type { Permission } from '../../api/types';
import { Spinner } from '../../components/Spinner';
import { PERMISSION_GROUPS } from './permissions';

/** v0.0.4 🍊 Grouped permission checkboxes (used by the hire form and the inspector). */
export function PermissionChecklist({
  value,
  onToggle,
  disabled = false,
  pending = null,
}: {
  value: readonly Permission[];
  onToggle: (permission: Permission, enabled: boolean) => void;
  disabled?: boolean;
  /** Permission whose change is being saved (shows a spinner). */
  pending?: Permission | null;
}) {
  const enabled = new Set(value);
  return (
    <div className="@container">
      <div className="grid gap-3 @lg:grid-cols-2">
      {PERMISSION_GROUPS.map((group) => (
        <fieldset key={group.label} className="rounded-xl border border-line p-2.5">
          <legend className="px-1 text-[11px] font-bold tracking-wider text-ink-3 uppercase">{group.label}</legend>
          <div className="space-y-1">
            {group.items.map((item) => {
              const checked = enabled.has(item.key);
              return (
                <label
                  key={item.key}
                  title={item.hint}
                  className={clsx(
                    'flex cursor-pointer items-start gap-2 rounded-lg px-1.5 py-1 hover:bg-surface-2',
                    (disabled || pending !== null) && 'cursor-not-allowed opacity-70',
                  )}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    disabled={disabled || pending !== null}
                    onChange={(e) => onToggle(item.key, e.target.checked)}
                    className="mt-0.5 size-4 shrink-0 accent-[var(--accent)]"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-1.5 text-sm font-medium">
                      {item.label}
                      {item.risky ? <span className="rounded bg-danger-soft px-1 text-[9px] font-bold text-danger-ink uppercase">risky</span> : null}
                      {pending === item.key ? <Spinner size={12} /> : null}
                    </span>
                    <span className="block text-[11px] text-ink-3">{item.hint}</span>
                  </span>
                </label>
              );
            })}
          </div>
        </fieldset>
      ))}
      </div>
    </div>
  );
}
