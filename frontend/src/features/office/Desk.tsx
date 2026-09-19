import { motion } from 'motion/react';
import { memo } from 'react';
import { Badge } from '../../components/Badge';
import { DESK_STATE_META } from '../../lib/colors';
import { DeskScene } from './DeskScene';
import type { AgentDeskView } from './officeData';
import { SpeechBubble } from './SpeechBubble';

/** Stable pseudo-random blink offset so agents never blink in sync. */
function blinkDelay(agentId: string): number {
  let hash = 0;
  for (let i = 0; i < agentId.length; i++) hash = (hash * 33 + agentId.charCodeAt(i)) % 997;
  return (hash % 50) / 10;
}

/** v0.0.4 🍊 One occupied desk (props only): bubble above the head, animated scene, name plate. */
export const Desk = memo(function Desk({ onOpen, ...desk }: AgentDeskView & { onOpen: (agentId: string) => void }) {
  const label = `${desk.name}, ${desk.title}: ${DESK_STATE_META[desk.state].label}. ${desk.summary}`;
  return (
    <motion.button
      type="button"
      layout
      initial={{ opacity: 0, scale: 0.9, y: 8 }}
      animate={{ opacity: 1, scale: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.9 }}
      transition={{ type: 'spring', stiffness: 320, damping: 26 }}
      onClick={() => onOpen(desk.agentId)}
      aria-label={`${label} Open the inspector.`}
      data-state={desk.state}
      className="desk group flex min-w-0 flex-col rounded-2xl p-1.5 text-left transition-colors hover:bg-surface/60 focus-visible:bg-surface/60"
    >
      <SpeechBubble state={desk.state} module={desk.module} summary={desk.summary} poolSize={desk.poolSize} pendingBatches={desk.pendingBatches} />
      <div className="mt-1 transition-transform duration-200 group-hover:-translate-y-0.5">
        <DeskScene avatarKey={desk.avatarKey} color={desk.color} state={desk.state} blinkDelay={blinkDelay(desk.agentId)} />
      </div>
      <div className="-mt-1 flex min-w-0 items-center justify-center gap-1.5 px-1">
        <span className="size-2 shrink-0 rounded-full" style={{ background: desk.color }} aria-hidden="true" />
        <span className="truncate text-sm font-bold">{desk.name}</span>
        {desk.paused ? <Badge>Paused</Badge> : null}
      </div>
      <p className="truncate px-1 text-center text-[11px] text-ink-3">{desk.title}</p>
    </motion.button>
  );
});
