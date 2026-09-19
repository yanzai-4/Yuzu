import type { Incident } from '../../../api/types';
import { PersonAvatar } from '../../../components/Avatars';
import { Badge, HueChip } from '../../../components/Badge';
import { EmptyState } from '../../../components/EmptyState';
import { Icon } from '../../../components/Icon';
import { clockTime } from '../../../lib/time';
import type { PersonRef } from '../../../stores/people';

const STAGE_HUES: Record<Incident['stage'], string> = {
  INBOUND: '#e11d48',
  OUTBOUND: '#ea580c',
  BEHAVIOR: '#a855f7',
  GUARD: '#0d9488',
  HIGH_RISK: '#dc2626',
};

/** v0.0.4 🍊 One security incident (props only): stage, verdict, agent, reasons and the excerpt. */
function IncidentCard({ incident, agent }: { incident: Incident; agent: PersonRef | null }) {
  return (
    <li className="rounded-lg border border-warn-line/70 bg-surface p-2.5">
      <div className="flex flex-wrap items-center gap-1.5">
        <Icon name="shield" size={14} className="text-warn-line" />
        <HueChip hue={STAGE_HUES[incident.stage] ?? '#78716c'}>{incident.stage}</HueChip>
        <Badge tone="warn">{incident.verdict}</Badge>
        <span className="flex items-center gap-1 text-[11px] font-semibold text-ink-2">
          <PersonAvatar person={agent} size={14} /> {agent?.name ?? incident.agentId}
        </span>
        <span className="flex-1" />
        <time className="text-[10px] text-ink-3" title={incident.time}>
          {clockTime(incident.time)}
        </time>
      </div>
      {incident.reasons.length > 0 ? (
        <ul className="mt-1.5 list-disc space-y-0.5 pl-5 text-xs text-ink-2">
          {incident.reasons.map((reason, i) => (
            <li key={i}>{reason}</li>
          ))}
        </ul>
      ) : null}
      {incident.excerpt ? (
        <blockquote className="mt-1.5 rounded-md border-l-2 border-warn-line bg-warn-bg/40 px-2 py-1 font-mono text-[11px] break-words text-ink-2">
          {incident.excerpt}
        </blockquote>
      ) : null}
    </li>
  );
}

/** v0.0.4 🍊 Security incidents (props only), newest first. */
export function IncidentList({ incidents }: { incidents: { incident: Incident; agent: PersonRef | null }[] }) {
  if (incidents.length === 0) {
    return (
      <EmptyState icon="shield" title="No security incidents">
        Blocked inbound content, masked tool output and guard denials are listed here.
      </EmptyState>
    );
  }
  return (
    <ul className="space-y-1.5">
      {incidents.map(({ incident, agent }) => (
        <IncidentCard key={incident.id} incident={incident} agent={agent} />
      ))}
    </ul>
  );
}
