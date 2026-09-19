import { AnimatePresence, motion } from 'motion/react';
import { useState } from 'react';
import type { Agent, AgentStatus } from '../../api/types';
import { Button } from '../../components/Button';
import { controlAgent, retire } from './agentActions';

/** v0.0.4 🍊 Pause / Resume / Interrupt buttons and Retire with an inline confirmation. */
export function InspectorControls({ agent, status }: { agent: Agent; status: AgentStatus | null }) {
  const [busy, setBusy] = useState<'pause' | 'resume' | 'interrupt' | 'retire' | null>(null);
  const [confirming, setConfirming] = useState(false);
  const paused = agent.state === 'PAUSED' || status?.state === 'PAUSED';

  const run = async (key: NonNullable<typeof busy>, action: () => Promise<unknown>) => {
    setBusy(key);
    await action();
    setBusy(null);
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        {paused ? (
          <Button size="sm" variant="leaf" icon="play" loading={busy === 'resume'} onClick={() => run('resume', () => controlAgent(agent.agentId, 'resume'))}>
            Resume
          </Button>
        ) : (
          <Button size="sm" icon="pause" loading={busy === 'pause'} onClick={() => run('pause', () => controlAgent(agent.agentId, 'pause'))} title="Stop reading new messages until resumed">
            Pause
          </Button>
        )}
        <Button
          size="sm"
          icon="stop"
          loading={busy === 'interrupt'}
          onClick={() => run('interrupt', () => controlAgent(agent.agentId, 'interrupt'))}
          title="Cancel what it is doing right now"
        >
          Interrupt
        </Button>
        <span className="flex-1" />
        {!confirming ? (
          <Button size="sm" variant="danger" icon="trash" onClick={() => setConfirming(true)}>
            Retire
          </Button>
        ) : null}
      </div>
      <AnimatePresence>
        {confirming ? (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden"
          >
            <div role="alertdialog" aria-label={`Retire ${agent.name}`} className="flex flex-wrap items-center gap-2 rounded-xl border border-danger/40 bg-danger-soft px-3 py-2">
              <p className="min-w-0 flex-1 text-xs text-danger-ink">
                Retire <strong>{agent.name}</strong>? They leave the office and their desk is freed. This cannot be undone.
              </p>
              <Button size="xs" variant="ghost" onClick={() => setConfirming(false)}>
                Cancel
              </Button>
              <Button size="xs" variant="danger" icon="trash" loading={busy === 'retire'} onClick={() => run('retire', () => retire(agent.agentId))}>
                Yes, retire
              </Button>
            </div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
