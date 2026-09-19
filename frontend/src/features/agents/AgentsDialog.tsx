import { Modal } from '../../components/Modal';
import { Tabs } from '../../components/Tabs';
import { DESK_COUNT } from '../../stores/room';
import { useActiveAgents } from '../../stores/selectors';
import { closeDialog, openDialog, openInspector, useUiStore } from '../../stores/ui';
import { AgentRoster } from './AgentRoster';
import { HireAgentForm } from './HireAgentForm';

/** v0.0.4 🍊 "Coworkers" dialog: the roster and the hire form (role presets → POST agents). */
export function AgentsDialog() {
  const dialog = useUiStore((s) => s.dialog);
  const count = useActiveAgents().length;
  const open = dialog?.kind === 'coworkers';
  const view = dialog?.kind === 'coworkers' ? dialog.view : 'roster';

  return (
    <Modal
      open={open}
      onClose={closeDialog}
      icon="users"
      title="Coworkers"
      subtitle={`${count} of ${DESK_COUNT} desks taken · names are assigned by the backend (citrus fruits)`}
      width="max-w-3xl"
    >
      <Tabs
        label="Coworkers views"
        value={view}
        onChange={(v) => openDialog({ kind: 'coworkers', view: v })}
        tabs={[
          { id: 'roster', label: 'Team', icon: 'users', count },
          { id: 'hire', label: 'Hire', icon: 'plus' },
        ]}
        className="mb-4 w-fit"
      />
      {view === 'roster' ? (
        <AgentRoster onHire={() => openDialog({ kind: 'coworkers', view: 'hire' })} />
      ) : (
        <HireAgentForm
          onHired={(agent) => {
            closeDialog();
            openInspector(agent.agentId);
          }}
        />
      )}
    </Modal>
  );
}
