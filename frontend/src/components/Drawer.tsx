import { AnimatePresence, motion } from 'motion/react';
import { useId, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { IconButton } from './Button';
import { useOverlay } from './useOverlay';

/** v0.0.4 🍊 Right-hand side sheet (portal, backdrop, Escape to close, slides in). */
export function Drawer({
  open,
  onClose,
  title,
  header,
  children,
}: {
  open: boolean;
  onClose: () => void;
  /** Accessible title (also shown when no custom header is given). */
  title: string;
  header?: ReactNode;
  children: ReactNode;
}) {
  const titleId = useId();
  const panelRef = useOverlay(open, onClose);
  return createPortal(
    <AnimatePresence>
      {open ? (
        <motion.div key="drawer" className="fixed inset-0 z-30" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <div className="absolute inset-0 bg-[#1a1208]/35" onClick={onClose} aria-hidden="true" />
          <motion.aside
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            tabIndex={-1}
            className="absolute inset-y-0 right-0 flex w-full max-w-[480px] flex-col border-l border-line bg-surface shadow-lg outline-none"
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', stiffness: 380, damping: 38 }}
          >
            <div className="flex items-start gap-2 border-b border-line bg-surface-2 px-4 py-3">
              <div id={titleId} className="min-w-0 flex-1">
                {header ?? <h2 className="text-base font-bold">{title}</h2>}
              </div>
              <IconButton icon="x" label="Close" onClick={onClose} />
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
          </motion.aside>
        </motion.div>
      ) : null}
    </AnimatePresence>,
    document.body,
  );
}
