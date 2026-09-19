import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { useId, useState, type ReactNode } from 'react';
import { Icon } from './Icon';

/** v0.0.4 🍊 Disclosure section with an animated body. */
export function Collapsible({
  title,
  defaultOpen = false,
  children,
  className,
  meta,
}: {
  title: ReactNode;
  defaultOpen?: boolean;
  children: ReactNode;
  className?: string;
  /** Right-aligned extra (count, badge). */
  meta?: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const bodyId = useId();
  return (
    <div className={className}>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={bodyId}
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-1.5 rounded-md py-1 text-left text-xs font-semibold text-ink-2 hover:text-ink"
      >
        <Icon name="chevronRight" size={14} className={clsx('transition-transform', open && 'rotate-90')} />
        <span className="min-w-0 flex-1 truncate">{title}</span>
        {meta}
      </button>
      <AnimatePresence initial={false}>
        {open ? (
          <motion.div
            id={bodyId}
            key="body"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="overflow-hidden"
          >
            <div className="pt-1.5">{children}</div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
