import type { RoleKey } from '../../api/types';
import { NO_FILTER, type TraceFilter } from '../../stores/ui';
import type { InsightsTab, SimView, TasksView, TraceView, UsageDimension } from '../../stores/ui';

/** v0.0.26 🍊 The six steps of the guided tour (also the URL hash). */
export type DemoStepId = 'triage' | 'tiering' | 'subconscious' | 'safety' | 'memory' | 'business';

/** v0.0.26 🍊 Every region a step can highlight (closed union: a typo is a type error). */
export type SpotlightId =
  | 'chat'
  | 'office'
  | 'insights.tasks.board'
  | 'insights.usage.totals'
  | 'insights.usage.breakdown'
  | 'insights.trace.events'
  | 'insights.trace.incidents'
  | 'insights.sim'
  | 'inspector.workingMemory';

/** v0.0.26 🍊 The complete view state a step wants (never a patch: every field is set). */
export interface DemoView {
  insightsTab: InsightsTab;
  tasksView: TasksView | null;
  simView: SimView | null;
  traceView: TraceView | null;
  usageDimension: UsageDimension | null;
  traceFilter: TraceFilter;
  /** Role of the agent whose inspector opens; null closes the drawer. */
  inspectorRole: RoleKey | null;
}

/** v0.0.26 🍊 One step of the guided tour. */
export interface DemoStep {
  id: DemoStepId;
  n: number;
  label: string;
  blurb: string;
  view: DemoView;
  spotlight: readonly SpotlightId[];
}

const BASE: DemoView = {
  insightsTab: 'tasks',
  tasksView: null,
  simView: null,
  traceView: null,
  usageDimension: null,
  traceFilter: NO_FILTER,
  inspectorRole: null,
};

/** v0.0.26 🍊 The guided tour, in presentation order. */
export const DEMO_STEPS: readonly DemoStep[] = [
  {
    id: 'triage',
    n: 1,
    label: 'Triage',
    blurb:
      'A secretary module fronts every agent. A message only wakes the agents it concerns — the others keep working. One request, split into tickets, assigned by the PM.',
    view: { ...BASE, insightsTab: 'tasks', tasksView: 'board' },
    spotlight: ['office', 'insights.tasks.board'],
  },
  {
    id: 'tiering',
    n: 2,
    label: 'Tiering',
    blurb:
      'The main consciousness speaks near-natural JSON. Dirty work goes to cheaper models: gpt-5 for MAIN, gpt-5-nano for compaction and monitoring.',
    view: { ...BASE, insightsTab: 'usage', usageDimension: 'model' },
    spotlight: ['insights.usage.totals', 'insights.usage.breakdown'],
  },
  {
    id: 'subconscious',
    n: 3,
    label: 'Subconscious',
    blurb:
      'Asynchronous subconscious modules watch the main thread, offer ideas, criticise its judgement, and decide what gets remembered.',
    view: {
      ...BASE,
      insightsTab: 'trace',
      traceView: 'events',
      traceFilter: { ...NO_FILTER, module: 'SUBCONSCIOUS' },
    },
    spotlight: ['insights.trace.events'],
  },
  {
    id: 'safety',
    n: 4,
    label: 'Safety',
    blurb:
      'Nothing enters or leaves unsearched. INBOUND and OUTBOUND are the AI review; GUARD is the hard-coded one. Both are needed.',
    view: { ...BASE, insightsTab: 'trace', traceView: 'incidents' },
    spotlight: ['chat', 'insights.trace.incidents'],
  },
  {
    id: 'memory',
    n: 5,
    label: 'Memory',
    blurb:
      'Three tiers: working memory that self-compacts (never the last 10), habit memory consulted before the main thread runs, and deep memory retrieved asynchronously into the pool.',
    view: {
      ...BASE,
      insightsTab: 'trace',
      traceView: 'events',
      traceFilter: { ...NO_FILTER, module: 'WM_COMPACTOR' },
      inspectorRole: 'RESEARCHER',
    },
    spotlight: ['inspector.workingMemory'],
  },
  {
    id: 'business',
    n: 6,
    label: 'Business',
    blurb:
      'Executed, rejected by a human, blocked by the guard. Every email and every trade carries its status and its reason.',
    view: { ...BASE, insightsTab: 'sim', simView: 'trades' },
    spotlight: ['chat', 'insights.sim'],
  },
];

/** v0.0.26 🍊 The step with this id, or null when the id is unknown. */
export function stepById(id: string): DemoStep | null {
  return DEMO_STEPS.find((s) => s.id === id) ?? null;
}
