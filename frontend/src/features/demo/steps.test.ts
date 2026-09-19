import { beforeEach, describe, expect, it } from 'vitest';
import type { Agent } from '../../api/types';
import type { SpotlightId } from './steps';
import { useRoomStore } from '../../stores/room';
import { useDemoStore } from '../../stores/demo';
import { useUiStore } from '../../stores/ui';
import { applyDemoStep, exitDemo } from './applyDemoStep';
import { DEMO_STEPS, stepById } from './steps';

function step(id: string) {
  const found = stepById(id);
  if (!found) throw new Error(`no step ${id}`);
  return found;
}

const lime = { agentId: 'agent-lime', role: 'RESEARCHER', state: 'ACTIVE' } as unknown as Agent;

beforeEach(() => {
  useUiStore.setState({ insightsTab: 'tasks', inspectorAgentId: null, dialog: null, traceId: null });
  useDemoStore.setState({ activeStepId: null, spotlight: new Set<SpotlightId>() });
  useRoomStore.setState({ agents: { [lime.agentId]: lime } });
});

describe('applyDemoStep', () => {
  it('applies every step without throwing', () => {
    for (const s of DEMO_STEPS) expect(() => applyDemoStep(s)).not.toThrow();
  });

  it('sets the insights tab and sub-tab of step 6', () => {
    applyDemoStep(step('business'));
    const ui = useUiStore.getState();
    expect(ui.insightsTab).toBe('sim');
    expect(ui.simView).toBe('trades');
  });

  it('sets the trace module filter of step 3', () => {
    applyDemoStep(step('subconscious'));
    expect(useUiStore.getState().traceFilter.module).toBe('SUBCONSCIOUS');
  });

  it('records the active step and its spotlight', () => {
    applyDemoStep(step('safety'));
    const demo = useDemoStore.getState();
    expect(demo.activeStepId).toBe('safety');
    expect(demo.spotlight.has('insights.trace.incidents')).toBe(true);
    expect(demo.spotlight.has('office')).toBe(false);
  });

  it('opens the researcher inspector on step 5', () => {
    applyDemoStep(step('memory'));
    expect(useUiStore.getState().inspectorAgentId).toBe('agent-lime');
  });

  it('closes the inspector again when a later step does not want it', () => {
    applyDemoStep(step('memory'));
    applyDemoStep(step('triage'));
    expect(useUiStore.getState().inspectorAgentId).toBeNull();
  });

  it('still applies the trace state when no researcher is active', () => {
    useRoomStore.setState({ agents: {} });
    applyDemoStep(step('memory'));
    const ui = useUiStore.getState();
    expect(ui.inspectorAgentId).toBeNull();
    expect(ui.insightsTab).toBe('trace');
    expect(ui.traceFilter.module).toBe('WM_COMPACTOR');
  });

  it('ignores retired agents when resolving the role', () => {
    useRoomStore.setState({ agents: { [lime.agentId]: { ...lime, state: 'RETIRED' } as Agent } });
    applyDemoStep(step('memory'));
    expect(useUiStore.getState().inspectorAgentId).toBeNull();
  });

  it('closes any open dialog and trace waterfall', () => {
    useUiStore.setState({ dialog: { kind: 'console' }, traceId: 'trace-1' });
    applyDemoStep(step('triage'));
    const ui = useUiStore.getState();
    expect(ui.dialog).toBeNull();
    expect(ui.traceId).toBeNull();
  });
});

describe('exitDemo', () => {
  it('clears the step and spotlight but leaves the view alone', () => {
    applyDemoStep(step('business'));
    exitDemo();
    const demo = useDemoStore.getState();
    expect(demo.activeStepId).toBeNull();
    expect(demo.spotlight.size).toBe(0);
    expect(useUiStore.getState().insightsTab).toBe('sim');
  });
});
