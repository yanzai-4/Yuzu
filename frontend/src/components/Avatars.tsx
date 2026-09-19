import clsx from 'clsx';
import { memo } from 'react';
import type { ChatMessage } from '../api/types';
import { avatarKeyFromName } from '../lib/citrus';
import { colorFromName, readableInk } from '../lib/colors';
import { initials } from '../lib/format';
import type { PersonRef } from '../stores/people';
import { useRoomStore } from '../stores/room';
import { CitrusAvatar } from './citrus/CitrusAvatar';
import { Icon } from './Icon';

/** v0.0.4 🍊 Round initials avatar of a human. */
export const UserAvatar = memo(function UserAvatar({
  name,
  color,
  size = 28,
  className,
}: {
  name: string;
  color?: string | null;
  size?: number;
  className?: string;
}) {
  const bg = color || colorFromName(name);
  return (
    <span
      aria-hidden="true"
      className={clsx('inline-grid shrink-0 place-items-center rounded-full font-bold ring-2 ring-surface', className)}
      style={{ width: size, height: size, background: bg, color: readableInk(bg), fontSize: Math.max(9, size * 0.38) }}
    >
      {initials(name)}
    </span>
  );
});

/** v0.0.4 🍊 Avatar of an agent by id (falls back to the citrus named like the agent). */
export const AgentAvatar = memo(function AgentAvatar({
  agentId,
  name,
  size = 28,
  className,
}: {
  agentId: string;
  name?: string;
  size?: number;
  className?: string;
}) {
  const agent = useRoomStore((s) => s.agents[agentId]);
  const label = agent?.name ?? name ?? agentId;
  return (
    <CitrusAvatar
      avatarKey={agent?.avatarKey ?? avatarKeyFromName(label)}
      color={agent?.color ?? ''}
      size={size}
      title={label}
      dimmed={agent?.state === 'RETIRED'}
      className={className}
    />
  );
});

/** v0.0.4 🍊 Avatar of a chat author: citrus for agents, initials for humans, a spark for the system. */
export const AuthorAvatar = memo(function AuthorAvatar({
  kind,
  id,
  name,
  size = 32,
}: {
  kind: ChatMessage['authorKind'];
  id: string;
  name: string;
  size?: number;
}) {
  const userColor = useRoomStore((s) => (kind === 'HUMAN' ? s.users[id]?.color : undefined));
  if (kind === 'AGENT') return <AgentAvatar agentId={id} name={name} size={size} />;
  if (kind === 'HUMAN') return <UserAvatar name={name} color={userColor} size={size} />;
  return (
    <span className="grid shrink-0 place-items-center rounded-full bg-surface-3 text-ink-3" style={{ width: size, height: size }}>
      <Icon name="sparkles" size={size * 0.5} />
    </span>
  );
});

/** v0.0.4 🍊 Props-only avatar of a resolved person (citrus for agents, initials for humans). */
export const PersonAvatar = memo(function PersonAvatar({
  person,
  size = 22,
  className,
}: {
  person: PersonRef | null;
  size?: number;
  className?: string;
}) {
  if (!person) return null;
  if (person.kind === 'agent') {
    return (
      <CitrusAvatar
        avatarKey={person.avatarKey ?? avatarKeyFromName(person.name)}
        color={person.color ?? ''}
        size={size}
        title={person.name}
        dimmed={person.retired}
        className={className}
      />
    );
  }
  return <UserAvatar name={person.name} color={person.color} size={size} className={className} />;
});

/** v0.0.4 🍊 Avatar for any id: agent (citrus) or human (initials); null when unknown. */
export const AssigneeAvatar = memo(function AssigneeAvatar({ id, size = 22 }: { id: string; size?: number }) {
  const agent = useRoomStore((s) => s.agents[id]);
  const user = useRoomStore((s) => s.users[id]);
  if (agent) return <AgentAvatar agentId={id} size={size} />;
  if (user) return <UserAvatar name={user.username} color={user.color} size={size} />;
  return null;
});
