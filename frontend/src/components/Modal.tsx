import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { useId, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { IconButton } from './Button';
import { Icon, type IconName } from './Icon';
import { useOverlay } from './useOverlay';

/** v0.0.4 🍊 Props of the modal dialog. */
export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  subtitle?: ReactNode;
  icon?: IconName;
  children: ReactNode;
  footer?: ReactNode;
  /** Tailwind max-width class of the panel. */
  width?: string;
}

/** v0.0.4 🍊 Centered modal dialog (portal, backdrop, Escape to close, animated). */
export function Modal({ open, onClose, title, subtitle, icon, children, footer, width = 'max-w-2xl' }: ModalProps) {
  const titleId = useId();
  const panelRef = useOverlay(open, onClose);
  return createPortal(
    <AnimatePresence>
      {open ? (
        <motion.div
          key="modal"
          className="fixed inset-0 z-40 flex items-start justify-center overflow-y-auto p-3 sm:p-6 sm:pt-[7vh]"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.15 }}
        >
          <div className="fixed inset-0 bg-[#1a1208]/45 backdrop-blur-[2px]" onClick={onClose} aria-hidden="true" />
          <motion.div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            tabIndex={-1}
            className={clsx(
              'relative flex max-h-[calc(100dvh-1.5rem)] w-full flex-col overflow-hidden rounded-2xl border border-line bg-surface shadow-lg outline-none sm:max-h-[86vh]',
              width,
            )}
            initial={{ y: 18, scale: 0.98 }}
            animate={{ y: 0, scale: 1 }}
            exit={{ y: 10, scale: 0.98 }}
            transition={{ type: 'spring', stiffness: 420, damping: 32 }}
          >
            <header className="flex items-start gap-3 border-b border-line bg-surface-2 px-4 py-3">
              {icon ? (
                <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg bg-accent-soft text-accent-soft-ink">
                  <Icon name={icon} size={17} />
                </span>
              ) : null}
              <div className="min-w-0 flex-1">
                <h2 id={titleId} className="text-base font-bold text-ink">
                  {title}
                </h2>
                {subtitle ? <p className="text-xs text-ink-3">{subtitle}</p> : null}
              </div>
              <IconButton icon="x" label="Close" onClick={onClose} />
            </header>
            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">{children}</div>
            {footer ? <footer className="border-t border-line bg-surface-2 px-4 py-3">{footer}</footer> : null}
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>,
    document.body,
  );
}
