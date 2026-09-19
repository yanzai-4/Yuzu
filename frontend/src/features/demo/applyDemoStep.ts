import type { Agent, RoleKey } from '../../api/types';
import { useDemoStore } from '../../stores/demo';
import { useRoomStore } from '../../stores/room';
import { useUiStore } from '../../stores/ui';
import type { DemoStep, SpotlightId } from './steps';

/** v0.0.33 🍊 The id of the first active agent with this role, or null. */
function agentIdByRole(role: RoleKey | null): string | null {
  if (!role) return null;
  const agents = Object.values(useRoomStore.getState().agents) as Agent[];
  return agents.find((a) => a.role === role && a.state === 'ACTIVE')?.agentId ?? null;
}

/**
 * v0.0.33 🍊 Puts the workspace into a tour step's view state. The only code that maps a step onto
 * the stores. View-only: it never posts a message or mutates the simulated world.
 */
export function applyDemoStep(step: DemoStep): void {
  const { view } = step;
  const current = useUiStore.getState();
  useUiStore.setState({
    insightsTab: view.insightsTab,
    tasksView: view.tasksView ?? current.tasksView,
    simView: view.simView ?? current.simView,
    traceView: view.traceView ?? current.traceView,
    usageDimension: view.usageDimension ?? current.usageDimension,
    traceFilter: view.traceFilter,
    inspectorAgentId: agentIdByRole(view.inspectorRole),
    // A tour step owns the whole screen: no dialog or waterfall may sit on top of it.
    dialog: null,
    traceId: null,
  });
  useDemoStore.setState({ activeStepId: step.id, spotlight: new Set(step.spotlight) });
}

/** v0.0.33 🍊 Leaves the tour: clears the step and the spotlight, and leaves the view where it is. */
export function exitDemo(): void {
  useDemoStore.setState({ activeStepId: null, spotlight: new Set<SpotlightId>() });
}
