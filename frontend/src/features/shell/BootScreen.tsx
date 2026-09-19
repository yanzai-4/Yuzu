import { motion } from 'motion/react';
import { resyncRoom } from '../../api/stream';
import { Button } from '../../components/Button';
import { CitrusAvatar } from '../../components/citrus/CitrusAvatar';
import { Spinner } from '../../components/Spinner';
import { useConnectionStore } from '../../stores/connection';

/** v0.0.4 🍊 Shown until the first snapshot arrives; explains retries when the server is unreachable. */
export function BootScreen() {
  const attempts = useConnectionStore((s) => s.attempts);
  const failing = attempts > 0;
  return (
    <div className="grid flex-1 place-items-center p-6">
      <div className="flex max-w-sm flex-col items-center gap-3 text-center">
        <motion.div animate={{ y: [0, -6, 0] }} transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}>
          <CitrusAvatar avatarKey="yuzu" color="#f5b700" size={64} expression={failing ? 'worried' : 'happy'} />
        </motion.div>
        {failing ? (
          <>
            <p className="text-sm font-semibold">Can't reach the Yuzu server</p>
            <p className="text-xs text-ink-3">
              Retrying automatically (attempt {attempts}). Make sure the backend is running on port 8080, or start the
              demo with <code className="font-mono">npm run dev:mock</code>.
            </p>
            <Button size="sm" icon="refresh" onClick={resyncRoom}>
              Retry now
            </Button>
          </>
        ) : (
          <p className="flex items-center gap-2 text-sm text-ink-3">
            <Spinner /> Opening the office…
          </p>
        )}
      </div>
    </div>
  );
}
