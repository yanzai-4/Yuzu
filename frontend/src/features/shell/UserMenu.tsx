import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useRef, useState } from 'react';
import { UserAvatar } from '../../components/Avatars';
import { Icon, type IconName } from '../../components/Icon';
import { setThemePref, usePrefsStore, type ThemePref } from '../../stores/prefs';
import { signOut, useSessionStore } from '../../stores/session';

const THEMES: { id: ThemePref; label: string; icon: IconName }[] = [
  { id: 'system', label: 'System', icon: 'monitor' },
  { id: 'light', label: 'Light', icon: 'sun' },
  { id: 'dark', label: 'Dark', icon: 'moon' },
];

/** v0.0.4 🍊 Current-user chip with a small menu (theme, leave the workspace). */
export function UserMenu({ compact = false }: { compact?: boolean }) {
  const user = useSessionStore((s) => s.user);
  const theme = usePrefsStore((s) => s.theme);
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setOpen(false);
    document.addEventListener('pointerdown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!user) return null;
  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="flex h-8 items-center gap-2 rounded-full border border-line bg-surface py-0.5 pr-2.5 pl-0.5 text-sm font-semibold hover:bg-surface-2"
      >
        <UserAvatar name={user.username} color={user.color} size={26} />
        {compact ? null : <span className="max-w-32 truncate">{user.username}</span>}
        <Icon name="chevronDown" size={14} className="text-ink-3" />
      </button>
      <AnimatePresence>
        {open ? (
          <motion.div
            role="menu"
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.12 }}
            className="absolute right-0 z-40 mt-2 w-60 rounded-xl border border-line bg-surface p-2 shadow-md"
          >
            <div className="px-2 pt-1 pb-2">
              <p className="text-sm font-bold">{user.username}</p>
              <p className="font-mono text-[11px] text-ink-3">{user.id}</p>
            </div>
            <p className="px-2 pb-1 text-[11px] font-bold tracking-wider text-ink-3 uppercase">Theme</p>
            <div className="grid grid-cols-3 gap-1 px-1 pb-2">
              {THEMES.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  role="menuitemradio"
                  aria-checked={theme === t.id}
                  onClick={() => setThemePref(t.id)}
                  className={clsx(
                    'flex flex-col items-center gap-1 rounded-lg border py-1.5 text-[11px] font-semibold',
                    theme === t.id ? 'border-accent bg-accent-soft text-accent-soft-ink' : 'border-line text-ink-2 hover:bg-surface-2',
                  )}
                >
                  <Icon name={t.icon} size={15} />
                  {t.label}
                </button>
              ))}
            </div>
            <button
              type="button"
              role="menuitem"
              onClick={signOut}
              className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-sm text-ink-2 hover:bg-surface-2 hover:text-ink"
            >
              <Icon name="logout" size={15} /> Leave workspace
            </button>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
