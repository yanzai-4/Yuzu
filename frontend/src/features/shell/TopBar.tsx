import { IS_MOCK } from '../../api/client';
import { Badge } from '../../components/Badge';
import { Button } from '../../components/Button';
import { CitrusAvatar } from '../../components/citrus/CitrusAvatar';
import { plural } from '../../lib/format';
import { useRoomStore } from '../../stores/room';
import { useActiveAgents } from '../../stores/selectors';
import { openDialog } from '../../stores/ui';
import { ConnectionIndicator } from './ConnectionIndicator';
import { UserMenu } from './UserMenu';

/** v0.0.4 🍊 Top bar: product name, room, live indicator, Coworkers / Console buttons and the user chip. */
export function TopBar({ compact }: { compact: boolean }) {
  const roomName = useRoomStore((s) => s.roomName);
  const humans = useRoomStore((s) => Object.keys(s.users).length);
  const agents = useActiveAgents().length;
  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b border-line bg-surface px-3 sm:gap-3 sm:px-4">
      <div className="flex shrink-0 items-center gap-1.5">
        <CitrusAvatar avatarKey="yuzu" color="#f5b700" size={30} />
        <span className="text-lg font-extrabold tracking-tight whitespace-nowrap">Yuzu 🍊</span>
      </div>
      <span className="hidden h-6 w-px bg-line sm:block" aria-hidden="true" />
      <div className="hidden min-w-0 sm:block">
        <p className="truncate text-sm leading-tight font-semibold">{roomName || 'Joining…'}</p>
        <p className="truncate text-[11px] leading-tight text-ink-3">
          {plural(agents, 'coworker')} · {plural(humans, 'human')}
        </p>
      </div>
      <ConnectionIndicator compact={compact} />
      {IS_MOCK ? (
        <Badge tone="warn" title="Running against the in-memory mock backend (VITE_MOCK=1)">
          Mock
        </Badge>
      ) : null}
      <div className="flex-1" />
      <Button
        icon="users"
        aria-label="Coworkers"
        title="Coworkers: list and hire agents"
        onClick={() => openDialog({ kind: 'coworkers', view: 'roster' })}
        square={compact}
      >
        {compact ? null : 'Coworkers'}
      </Button>
      <Button
        icon="sliders"
        aria-label="Console"
        title="Console: model settings"
        onClick={() => openDialog({ kind: 'console' })}
        square={compact}
      >
        {compact ? null : 'Console'}
      </Button>
      <UserMenu compact={compact} />
    </header>
  );
}
