import clsx from 'clsx';
import { useEffect } from 'react';
import { Icon, type IconName } from '../../components/Icon';
import { useChatStore } from '../../stores/chat';
import { markChatSeen, setMobilePane, useUiStore, type Pane } from '../../stores/ui';

const PANES: { id: Pane; label: string; icon: IconName }[] = [
  { id: 'chat', label: 'Chat', icon: 'message' },
  { id: 'office', label: 'Office', icon: 'building' },
  { id: 'insights', label: 'Insights', icon: 'chart' },
];

/** v0.0.4 🍊 Bottom tab bar of the narrow layout (Chat / Office / Insights) with an unread badge. */
export function MobileTabBar() {
  const pane = useUiStore((s) => s.mobilePane);
  const seen = useUiStore((s) => s.chatSeen);
  const total = useChatStore((s) => s.order.length);

  useEffect(() => {
    if (pane === 'chat') markChatSeen(total);
  }, [pane, total]);

  const unread = pane === 'chat' ? 0 : Math.max(0, total - seen);
  return (
    <nav
      aria-label="Panes"
      className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-3 border-t border-line bg-surface pb-[env(safe-area-inset-bottom)]"
    >
      {PANES.map((p) => (
        <button
          key={p.id}
          type="button"
          aria-current={pane === p.id ? 'page' : undefined}
          onClick={() => setMobilePane(p.id)}
          className={clsx(
            'relative flex h-14 flex-col items-center justify-center gap-0.5 text-[11px] font-semibold',
            pane === p.id ? 'text-accent' : 'text-ink-3 hover:text-ink',
          )}
        >
          <Icon name={p.icon} size={20} />
          {p.label}
          {p.id === 'chat' && unread > 0 ? (
            <span className="absolute top-1.5 left-[calc(50%+6px)] min-w-4 rounded-full bg-accent px-1 text-[10px] leading-4 text-accent-ink">
              {unread > 99 ? '99+' : unread}
            </span>
          ) : null}
          {pane === p.id ? <span className="absolute inset-x-8 top-0 h-0.5 rounded-b bg-accent" aria-hidden="true" /> : null}
        </button>
      ))}
    </nav>
  );
}
