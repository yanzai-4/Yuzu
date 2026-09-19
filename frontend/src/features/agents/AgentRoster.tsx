import { useState } from 'react';
import { AgentAvatar } from '../../components/Avatars';
import { Badge, ModuleChip } from '../../components/Badge';
import { Button } from '../../components/Button';
import { EmptyState } from '../../components/EmptyState';
import { DESK_STATE_META } from '../../lib/colors';
import { useRoomStore } from '../../stores/room';
import { useActiveAgents } from '../../stores/selectors';
import { openInspector } from '../../stores/ui';
import { seedDemoTeam } from './agentActions';
import { ROLE_LABELS } from './permissions';

/** v0.0.4 🍊 List of the room's agents with their live state; click one to open the inspector. */
export function AgentRoster({ onHire }: { onHire: () => void }) {
  const agents = useActiveAgents();
  const statuses = useRoomStore((s) => s.statuses);
  const [seeding, setSeeding] = useState(false);

  if (agents.length === 0) {
    return (
      <EmptyState icon="users" title="No coworkers yet">
        <p>Hire your first AI coworker, or bring in the demo team (Yuzu, Lime, Kumquat and Pomelo).</p>
        <div className="mt-3 flex justify-center gap-2">
          <Button size="sm" variant="primary" icon="plus" onClick={onHire}>
            Hire a coworker
          </Button>
          <Button
            size="sm"
            icon="sparkles"
            loading={seeding}
            onClick={async () => {
              setSeeding(true);
              await seedDemoTeam();
              setSeeding(false);
            }}
          >
            Seed demo team
          </Button>
        </div>
      </EmptyState>
    );
  }

  return (
    <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line">
      {agents.map((agent) => {
        const status = statuses[agent.agentId];
        const state = agent.state === 'PAUSED' ? 'PAUSED' : (status?.state ?? 'IDLE');
        const meta = DESK_STATE_META[state];
        return (
          <li key={agent.agentId}>
            <button
              type="button"
              onClick={() => openInspector(agent.agentId)}
              className="flex w-full items-center gap-3 px-3 py-2.5 text-left hover:bg-surface-2"
            >
              <AgentAvatar agentId={agent.agentId} size={40} />
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-1.5">
                  <span className="text-sm font-bold">{agent.name}</span>
                  <span className="text-xs text-ink-3">{agent.title}</span>
                  <Badge>{ROLE_LABELS[agent.role] ?? agent.role}</Badge>
                  {agent.state === 'PAUSED' ? <Badge tone="warn">Paused</Badge> : null}
                </span>
                <span className="mt-0.5 flex min-w-0 items-center gap-1.5 text-xs text-ink-2">
                  <span className="size-2 shrink-0 rounded-full" style={{ background: meta.color }} aria-hidden="true" />
                  <span className="shrink-0 font-semibold">{meta.label}</span>
                  {status?.bubble?.module ? <ModuleChip module={status.bubble.module} /> : null}
                  <span className="truncate text-ink-3">{status?.bubble?.summary}</span>
                </span>
              </span>
              <span className="hidden shrink-0 font-mono text-[10px] text-ink-3 sm:block">{agent.agentId}</span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
