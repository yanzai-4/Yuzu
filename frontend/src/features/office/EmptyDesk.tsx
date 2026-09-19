import { motion } from 'motion/react';
import { memo } from 'react';
import { Icon } from '../../components/Icon';
import { DeskFurniture } from './DeskFurniture';

/** v0.0.4 🍊 A free desk (props only): dashed "Hire a coworker" slot. */
export const EmptyDesk = memo(function EmptyDesk({ seat, onHire }: { seat: number; onHire: (seat: number) => void }) {
  return (
    <motion.button
      type="button"
      layout
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      onClick={() => onHire(seat)}
      aria-label={`Desk ${seat + 1} is free: hire a coworker`}
      className="group flex min-w-0 flex-col rounded-2xl border-2 border-dashed border-line-strong/80 p-1.5 text-ink-3 transition-colors hover:border-accent hover:bg-surface/50 hover:text-accent"
    >
      <div className="grid h-[70px] place-items-center">
        <span className="grid size-9 place-items-center rounded-full border-2 border-dashed border-current transition-transform group-hover:scale-110">
          <Icon name="plus" size={18} />
        </span>
      </div>
      <svg viewBox="0 0 160 132" className="block w-full opacity-45 transition-opacity group-hover:opacity-70" aria-hidden="true">
        <ellipse cx={80} cy={126} rx={66} ry={3.5} fill="#000" opacity={0.08} />
        <rect x={40} y={46} width={60} height={56} rx={17} fill="var(--line-strong)" />
        <DeskFurniture screenOn={false} />
      </svg>
      <span className="-mt-1 text-center text-sm font-bold">Hire a coworker</span>
      <span className="text-center text-[11px]">Desk {seat + 1} is free</span>
    </motion.button>
  );
});
