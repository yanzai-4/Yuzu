import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { memo, useEffect } from 'react';
import { dismissToast, useUiStore, type Toast } from '../stores/ui';
import { IconButton } from './Button';
import { Icon, type IconName } from './Icon';

const TONE: Record<Toast['tone'], { icon: IconName; bar: string; iconClass: string }> = {
  error: { icon: 'alert', bar: 'bg-danger', iconClass: 'text-danger' },
  warning: { icon: 'alert', bar: 'bg-warn-line', iconClass: 'text-warn-line' },
  success: { icon: 'check', bar: 'bg-leaf', iconClass: 'text-leaf' },
  info: { icon: 'info', bar: 'bg-info', iconClass: 'text-info' },
};

/** v0.0.4 🍊 One toast; dismisses itself after a few seconds. */
const ToastItem = memo(function ToastItem({ toast }: { toast: Toast }) {
  useEffect(() => {
    const timer = setTimeout(() => dismissToast(toast.id), toast.tone === 'error' ? 8000 : 5000);
    return () => clearTimeout(timer);
  }, [toast.id, toast.tone]);
  const tone = TONE[toast.tone];
  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 16, scale: 0.97 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, x: 40 }}
      transition={{ type: 'spring', stiffness: 500, damping: 36 }}
      role={toast.tone === 'error' ? 'alert' : 'status'}
      className="pointer-events-auto relative flex w-full gap-2.5 overflow-hidden rounded-xl border border-line bg-surface py-2.5 pr-2 pl-3.5 shadow-md"
    >
      <span className={clsx('absolute inset-y-0 left-0 w-1', tone.bar)} aria-hidden="true" />
      <Icon name={tone.icon} size={18} className={clsx('mt-0.5 shrink-0', tone.iconClass)} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5">
          <p className="text-sm font-semibold text-ink">{toast.title}</p>
          {toast.code ? (
            <code className="rounded bg-surface-3 px-1 py-px font-mono text-[10px] font-semibold text-ink-2">{toast.code}</code>
          ) : null}
        </div>
        {toast.message ? <p className="mt-0.5 text-xs break-words text-ink-2">{toast.message}</p> : null}
      </div>
      <IconButton icon="x" label="Dismiss" size="xs" onClick={() => dismissToast(toast.id)} />
    </motion.div>
  );
});

/** v0.0.4 🍊 Bottom-right stack of toasts (errors, confirmations). */
export function Toaster() {
  const toasts = useUiStore((s) => s.toasts);
  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed right-3 bottom-3 z-50 flex w-[min(380px,calc(100vw-1.5rem))] flex-col gap-2 max-[1099px]:bottom-16"
    >
      <AnimatePresence initial={false}>
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} />
        ))}
      </AnimatePresence>
    </div>
  );
}
