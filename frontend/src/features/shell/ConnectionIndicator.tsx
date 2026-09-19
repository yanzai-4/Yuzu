import clsx from 'clsx';
import { useShallow } from 'zustand/react/shallow';
import { resyncRoom } from '../../api/stream';
import { useConnectionStore } from '../../stores/connection';

const META = {
  connected: { label: 'Live', dot: 'bg-ok', text: 'text-ok' },
  connecting: { label: 'Connecting…', dot: 'bg-citrus animate-soft-pulse', text: 'text-ink-2' },
  reconnecting: { label: 'Reconnecting…', dot: 'bg-citrus animate-soft-pulse', text: 'text-ink-2' },
  idle: { label: 'Offline', dot: 'bg-ink-3', text: 'text-ink-3' },
} as const;

/** v0.0.4 🍊 Live connection pill (click to reconnect now while the stream is down). */
export function ConnectionIndicator({ compact = false }: { compact?: boolean }) {
  const { status, attempts, connectionId } = useConnectionStore(
    useShallow((s) => ({ status: s.status, attempts: s.attempts, connectionId: s.connectionId })),
  );
  const meta = META[status];
  const down = status === 'reconnecting' || status === 'idle';
  const title = down
    ? `Stream disconnected${attempts ? ` (attempt ${attempts})` : ''}. Click to reconnect now.`
    : `Realtime stream: ${meta.label.toLowerCase()}${connectionId ? ` (connection ${connectionId})` : ''}`;
  return (
    <button
      type="button"
      onClick={down ? resyncRoom : undefined}
      title={title}
      aria-label={`Connection: ${meta.label}`}
      className={clsx(
        'inline-flex h-7 items-center gap-1.5 rounded-full border border-line bg-surface px-2.5 text-xs font-semibold',
        meta.text,
        down ? 'hover:bg-surface-3' : 'cursor-default',
      )}
    >
      <span className={clsx('size-2 rounded-full', meta.dot)} aria-hidden="true" />
      {compact ? null : meta.label}
    </button>
  );
}
