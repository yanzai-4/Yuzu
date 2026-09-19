import { HueChip } from '../../components/Badge';
import { Panel } from '../../components/Panel';
import { DESK_STATE_META } from '../../lib/colors';
import { openHireDialog, openInspector } from '../../stores/ui';
import { OfficeFloor } from './OfficeFloor';
import { useOfficeData } from './useOfficeData';

/** Clicking an empty desk opens the hire form (the seat is informational: the backend seats agents). */
const hire = () => openHireDialog();

/** v0.0.4 🍊 Middle pane (container): wires useOfficeData() and the UI actions into the OfficeFloor. */
export function OfficePane() {
  const { desks, occupied, stateCounts } = useOfficeData();
  return (
    <Panel
      label="Office floor"
      icon="building"
      title="Office floor"
      subtitle={`${occupied} of ${desks.length} desks taken · click a coworker to inspect`}
      actions={
        <div className="hidden flex-wrap justify-end gap-1 sm:flex">
          {stateCounts.map(([state, n]) => (
            <HueChip key={state} hue={DESK_STATE_META[state].color} title={`${n} ${DESK_STATE_META[state].label.toLowerCase()}`}>
              {n} {DESK_STATE_META[state].label}
            </HueChip>
          ))}
        </div>
      }
      bodyClassName="office-floor overflow-y-auto @container"
    >
      <OfficeFloor desks={desks} onOpenAgent={openInspector} onHire={hire} />
    </Panel>
  );
}
