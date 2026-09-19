import { AgentAvatar } from '../../components/Avatars';
import { Panel } from '../../components/Panel';
import { plural } from '../../lib/format';
import { useRoomStore } from '../../stores/room';
import { useActiveAgents } from '../../stores/selectors';
import { Composer } from './Composer';
import { MessageList } from './MessageList';
import { TypingIndicator } from './TypingIndicator';

/** v0.0.4 🍊 Left pane: the group chat (messages, typing indicator, composer). */
export function ChatPane() {
  const roomName = useRoomStore((s) => s.roomName);
  const humans = useRoomStore((s) => Object.keys(s.users).length);
  const agents = useActiveAgents();
  return (
    <Panel
      label="Group chat"
      icon="message"
      title={roomName ? `# ${roomName}` : 'Group chat'}
      subtitle={`${plural(agents.length, 'AI coworker')} · ${plural(humans, 'human')}`}
      actions={
        <div className="flex -space-x-1.5" aria-hidden="true">
          {agents.slice(0, 5).map((a) => (
            <AgentAvatar key={a.agentId} agentId={a.agentId} size={24} />
          ))}
        </div>
      }
      bodyClassName="flex flex-col"
    >
      <div className="min-h-0 flex-1">
        <MessageList />
      </div>
      <TypingIndicator />
      <Composer />
    </Panel>
  );
}
