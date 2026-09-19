import { useEffect, type ReactNode } from 'react';
import type { Agent, AgentStatus, DeskState } from '../../api/types';
import { Badge, ModuleChip } from '../../components/Badge';
import { CitrusAvatar } from '../../components/citrus/CitrusAvatar';
import { Drawer } from '../../components/Drawer';
import { SectionTitle } from '../../components/Field';
import { Icon } from '../../components/Icon';
import { DESK_STATE_META } from '../../lib/colors';
import { clockTime } from '../../lib/time';
import { useRoomStore } from '../../stores/room';
import { closeInspector, useUiStore } from '../../stores/ui';
import { TaskListCard } from '../insights/tasks/TaskListCard';
import { approveList, refreshAgentTasks } from '../insights/tasks/taskActions';
import { useAgentTaskCard } from '../insights/tasks/useTasksData';
import { InspectorControls } from './InspectorControls';
import { RecentActivitySection, WorkingMemorySection } from './InspectorMemory';
import { LimitsSection, PermissionsSection, ProfileSection } from './InspectorSettings';
import { ROLE_LABELS } from './permissions';

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-2.5 px-4 py-4">
      <SectionTitle>{title}</SectionTitle>
      {children}
    </section>
  );
}

function deskState(agent: Agent, status: AgentStatus | null): DeskState {
  return agent.state === 'PAUSED' ? 'PAUSED' : (status?.state ?? 'IDLE');
}

/** v0.0.4 🍊 Drawer header: big citrus face, name, title, role and lifecycle badges. */
function InspectorHeader({ agent, status }: { agent: Agent; status: AgentStatus | null }) {
  const state = deskState(agent, status);
  return (
    <div className="flex items-center gap-3">
      <CitrusAvatar
        avatarKey={agent.avatarKey}
        color={agent.color}
        size={54}
        expression={state === 'PAUSED' ? 'sleeping' : state === 'ERROR' ? 'worried' : 'happy'}
      />
      <div className="min-w-0">
        <h2 className="text-lg leading-tight font-extrabold">{agent.name}</h2>
        <p className="truncate text-xs text-ink-2">{agent.title}</p>
        <div className="mt-1 flex flex-wrap items-center gap-1">
          <Badge>{ROLE_LABELS[agent.role] ?? agent.role}</Badge>
          <Badge tone={agent.state === 'PAUSED' ? 'warn' : 'leaf'}>{agent.state}</Badge>
          <span className="font-mono text-[10px] text-ink-3">{agent.agentId}</span>
        </div>
      </div>
    </div>
  );
}

function InspectorBody({ agent, status }: { agent: Agent; status: AgentStatus | null }) {
  const taskCard = useAgentTaskCard(agent.agentId);
  const state = deskState(agent, status);
  const meta = DESK_STATE_META[state];

  useEffect(() => {
    void refreshAgentTasks(agent.agentId);
  }, [agent.agentId]);

  return (
    <div className="divide-y divide-line">
      <Section title="Right now">
        <div className="rounded-xl border border-line bg-surface-2 p-3">
          <div className="flex flex-wrap items-center gap-1.5 text-sm font-semibold">
            <span className="size-2.5 rounded-full" style={{ background: meta.color }} aria-hidden="true" />
            {meta.label}
            {status?.bubble?.module ? <ModuleChip module={status.bubble.module} /> : null}
            {status?.time ? <span className="text-[11px] font-normal text-ink-3">since {clockTime(status.time)}</span> : null}
          </div>
          <p className="mt-1 text-sm text-ink-2">{status?.bubble?.summary || 'No live status yet.'}</p>
          <div className="mt-2 flex flex-wrap gap-3 text-[11px] text-ink-3">
            <span className="inline-flex items-center gap-1">
              <Icon name="inbox" size={12} /> {status?.poolSize ?? 0} in pool
            </span>
            <span className="inline-flex items-center gap-1">
              <Icon name="layers" size={12} /> {status?.pendingBatches ?? 0} pending batches
            </span>
            {status && status.activeModules.length > 0 ? (
              <span className="inline-flex flex-wrap items-center gap-1">
                active: {status.activeModules.map((m) => <ModuleChip key={m} module={m} />)}
              </span>
            ) : null}
          </div>
        </div>
        <InspectorControls agent={agent} status={status} />
      </Section>
      <Section title="Current tasks">
        {taskCard ? (
          <TaskListCard card={taskCard} showAgent={false} onApprove={approveList} />
        ) : (
          <p className="text-xs text-ink-3">No task list yet.</p>
        )}
      </Section>
      <Section title="Profile">
        <p className="text-[11px] text-ink-3">
          Role {ROLE_LABELS[agent.role] ?? agent.role} · hired {agent.createdTime}
        </p>
        <ProfileSection agent={agent} />
      </Section>
      <Section title="Permissions">
        <PermissionsSection agent={agent} />
      </Section>
      <Section title="Limits">
        <LimitsSection agent={agent} />
      </Section>
      <Section title="Working memory">
        <WorkingMemorySection agentId={agent.agentId} />
      </Section>
      <Section title="Recent activity">
        <RecentActivitySection agentId={agent.agentId} />
      </Section>
    </div>
  );
}

/** v0.0.4 🍊 Agent Inspector drawer: live status, controls, tasks, profile, permissions, limits, memory. */
export function AgentInspector() {
  const agentId = useUiStore((s) => s.inspectorAgentId);
  const agent = useRoomStore((s) => (agentId ? s.agents[agentId] : undefined));
  const status = useRoomStore((s) => (agentId ? (s.statuses[agentId] ?? null) : null));
  const open = Boolean(agent && agent.state !== 'RETIRED');
  return (
    <Drawer
      open={open}
      onClose={closeInspector}
      title={agent ? `${agent.name} inspector` : 'Agent inspector'}
      header={agent ? <InspectorHeader agent={agent} status={status} /> : undefined}
    >
      {agent && open ? <InspectorBody key={agent.agentId} agent={agent} status={status} /> : null}
    </Drawer>
  );
}
