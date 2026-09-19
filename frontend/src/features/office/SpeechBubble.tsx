import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { memo } from 'react';
import type { DeskState } from '../../api/types';
import { ModuleChip } from '../../components/Badge';
import { Icon } from '../../components/Icon';
import { DESK_STATE_META } from '../../lib/colors';
import { truncate } from '../../lib/format';

/** v0.0.4 🍊 Colored desk-state dot (pings while the agent is busy). */
export function StatusDot({ state }: { state: DeskState }) {
  const meta = DESK_STATE_META[state];
  const busy = state === 'WORKING' || state === 'TALKING' || state === 'THINKING';
  return (
    <span className="relative inline-flex size-2 shrink-0" title={meta.label}>
      {busy ? <span className="status-ping absolute inset-0 rounded-full" style={{ background: meta.color }} /> : null}
      <span className="relative inline-flex size-2 rounded-full" style={{ background: meta.color }} />
    </span>
  );
}

/**
 * v0.0.4 🍊 The box above an agent's head (props only): status dot, module chip, what it is doing,
 * and pool / pending-batch badges when non-zero.
 */
export const SpeechBubble = memo(function SpeechBubble({
  state,
  module,
  summary,
  poolSize,
  pendingBatches,
}: {
  state: DeskState;
  module: string;
  summary: string;
  poolSize: number;
  pendingBatches: number;
}) {
  const text = summary.trim() || (state === 'IDLE' ? 'Idle — waiting for something to do' : DESK_STATE_META[state].label);
  return (
    <div className={clsx('relative rounded-xl border bg-surface px-2 py-1.5 shadow-sm', state === 'ERROR' ? 'border-danger/50' : 'border-line')}>
      <div className="flex items-center gap-1.5">
        <StatusDot state={state} />
        <ModuleChip module={module || 'MONITOR'} className="min-w-0" />
        <span className="flex-1" />
        {poolSize > 0 ? (
          <span className="inline-flex items-center gap-0.5 text-[10px] font-semibold text-ink-3" title={`${poolSize} message(s) waiting in the pool`}>
            <Icon name="inbox" size={11} />
            {poolSize}
          </span>
        ) : null}
        {pendingBatches > 0 ? (
          <span className="inline-flex items-center gap-0.5 text-[10px] font-semibold text-ink-3" title={`${pendingBatches} action batch(es) pending`}>
            <Icon name="layers" size={11} />
            {pendingBatches}
          </span>
        ) : null}
      </div>
      <div className="relative mt-1 h-[2.7em] overflow-hidden text-[11px] leading-[1.3em] text-ink-2" title={text}>
        <AnimatePresence mode="popLayout" initial={false}>
          <motion.p
            key={text}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="line-clamp-2 break-words"
          >
            {truncate(text, 120)}
          </motion.p>
        </AnimatePresence>
      </div>
      <span
        aria-hidden="true"
        className="absolute -bottom-[5px] left-[43.75%] size-2.5 -translate-x-1/2 rotate-45 border-r border-b border-line bg-surface"
      />
    </div>
  );
});
