# Guided demo tour — design

> v0.0.26 🍊 A six-step guided tour over the existing three-pane workspace.

## 1. Goal

Give the workspace a guided tour: six numbered steps, each one jumping the UI to the state that
shows off one pillar of the product, with a one-line caption on screen.

The tour is **view-only**. It switches tabs, sets filters and opens the inspector. It never posts a
message, answers a card or mutates the simulated world, so every step is repeatable and a step can
never be "used up" mid-presentation.

## 2. Constraints

- The three-pane layout stays exactly as it is. The tour adds one bar under the top bar; it does not
  replace, wrap or reflow the panes. The "all three panes are alive at once" picture is the product's
  strongest frame and must survive.
- All on-screen text is English, like the rest of the UI.
- No router dependency. Navigation state lives in the existing Zustand stores.
- The bar costs at most ~60 px of height. The panes already need 1100 px of width; the tour must not
  compete for width.

## 3. Why this needs a refactor first

`insightsTab` already lives in `useUiStore`, but every **sub**-tab is component-local `useState`:

| View | State | Where |
|---|---|---|
| Insights tab | `insightsTab` | `stores/ui.ts` ✅ |
| Tasks: Task lists / Tickets | `view` | `features/insights/tasks/TasksTab.tsx` |
| Sim: Emails / Trades / Portfolios | `view` | `features/insights/sim/SimTab.tsx` |
| Trace: Live events / Incidents / Errors | `view` | `features/insights/trace/TraceTab.tsx` |
| Usage: Agent / Module / Tier / Model | `dimension` (and `asTable`) | `features/insights/usage/UsageView.tsx` |
| Trace agent/module/phase filter | `filter` | `features/insights/trace/EventStream.tsx` |

Four of the six tour steps target one of those. Lifting them into `useUiStore` is a precondition of
the feature, not incidental cleanup. It also makes the whole view state serializable, which is what
makes the URL hash in §6 almost free.

Only `dimension` is lifted from `UsageView`; `asTable` stays local, because no step drives it and
lifting it would widen the store for nothing.

`TraceFilters` is already props-only, so lifting `EventStream`'s filter is a one-line change there.

## 4. Architecture

Three new units, each with one job:

### 4.1 `features/demo/steps.ts` — the data

Six frozen records. No behaviour, no imports from components:

```ts
export interface DemoStep {
  id: DemoStepId;          // 'triage' | 'tiering' | ... — also the URL hash
  n: number;               // 1..6, shown on the chip and bound to the number key
  label: string;           // chip text
  blurb: string;           // the caption line
  view: DemoView;          // the COMPLETE view state this step wants
  spotlight: SpotlightId[];
}
```

`view` is complete, not a patch: a step that does not want the inspector open states
`inspectorAgentId: null`. Leaving a step therefore cleans up after itself without any teardown logic.

`DemoView` is a flat record of exactly these fields, all required:

```ts
interface DemoView {
  insightsTab: InsightsTab;
  tasksView: TasksView | null;      // null = leave at its default
  simView: SimView | null;
  traceView: TraceView | null;
  usageDimension: UsageDimension | null;
  traceFilter: TraceFilter;         // NO_FILTER when the step does not filter
  inspectorRole: RoleKey | null;    // null = drawer closed
}
```

Note `inspectorRole`, not `inspectorAgentId`: a step names the agent it opens **by role**, so it
still works after that agent is retired or under a different seed. `applyDemoStep` resolves the role
against the live roster and writes the resulting id into `useUiStore.inspectorAgentId`, or `null`
when no such agent is active.

### 4.2 `features/demo/applyDemoStep.ts` — the dispatcher

One pure-ish function: `applyDemoStep(step: DemoStep): void`. It writes `step.view` into `useUiStore`
and `step.spotlight` + `step.id` into `useDemoStore`. It is the only place that knows how a step maps
onto stores. Adding a seventh step means adding a record to §4.1 and nothing else.

### 4.3 `stores/demo.ts` — the tour's own state

```ts
interface DemoState {
  activeStepId: DemoStepId | null;   // null = tour not running
  spotlight: Set<SpotlightId>;
}
```

Separate from `useUiStore` on purpose: `useUiStore` describes the app, `useDemoStore` describes the
presentation layered over it. A component that reads neither is unaffected by the tour.

### 4.4 Components

- `features/demo/DemoBar.tsx` — the chip row plus the caption line. Rendered by `Workspace` between
  `TopBar` and `<main>`.
- `components/Spotlight.tsx` — a thin wrapper: `<Spotlight id="insights.trace.incidents">`. When its
  id is in `useDemoStore.spotlight` it adds a ring and a soft glow. It does **not** dim anything else;
  dimming the rest of the screen would destroy the "three panes are all alive" frame.

`SpotlightId` is a closed string-literal union declared once in `features/demo/steps.ts`, so a typo
in a step or at a wrapper site is a type error rather than a silently missing highlight. The full set
is the eight ids used in §5.

Call sites read state and know nothing about the tour. The tour is removable by deleting
`features/demo/`, `stores/demo.ts`, the `Spotlight` wrappers and one line in `Workspace`.

## 5. The six steps

| # | id | View state | Spotlight | Caption |
|---|---|---|---|---|
| 1 | `triage` | `insightsTab: 'tasks'`, `tasksView: 'board'` | `office`, `insights.tasks.board` | A secretary module fronts every agent. A message only wakes the agents it concerns — the others keep working. One request, split into tickets, assigned by the PM. |
| 2 | `tiering` | `insightsTab: 'usage'`, `usageDimension: 'model'` | `insights.usage.totals`, `insights.usage.breakdown` | The main consciousness speaks near-natural JSON. Dirty work goes to cheaper models: gpt-5 for MAIN, gpt-5-nano for compaction and monitoring. |
| 3 | `subconscious` | `insightsTab: 'trace'`, `traceView: 'events'`, `traceFilter.module: 'SUBCONSCIOUS'` | `insights.trace.events` | Asynchronous subconscious modules watch the main thread, offer ideas, criticise its judgement, and decide what gets remembered. |
| 4 | `safety` | `insightsTab: 'trace'`, `traceView: 'incidents'` | `chat`, `insights.trace.incidents` | Nothing enters or leaves unsearched. INBOUND / OUTBOUND are the AI review; GUARD is the hard-coded one. Both are needed. |
| 5 | `memory` | `insightsTab: 'trace'`, `traceView: 'events'`, `traceFilter.module: 'WM_COMPACTOR'`, `inspectorRole: 'RESEARCHER'` | `inspector.workingMemory` | Three tiers: working memory that self-compacts (never the last 10), habit memory consulted before the main thread runs, and deep memory retrieved asynchronously into the pool. |
| 6 | `business` | `insightsTab: 'sim'`, `simView: 'trades'` | `chat`, `insights.sim` | Executed, rejected by a human, blocked by the guard. Every email and every trade carries its status and its reason. |

Every step sets all other view fields to their defaults (`inspectorAgentId: null`, `dialog: null`,
`traceId: null`, and `traceFilter` cleared unless the step sets one).

## 6. Entry points

- **Chips** — click a chip to enter that step; click the active chip again to leave the tour.
- **Keyboard** — `1`–`6` select a step, `0` and `Escape` leave. Suppressed while an `input`,
  `textarea` or `contenteditable` has focus, so typing in the chat composer is never intercepted.
- **URL hash** — entering a step writes `#/demo/<id>`; on load, a matching hash applies that step.
  Leaving clears the hash. Implemented with `window.location.hash` and a `hashchange` listener; no
  router dependency.

Leaving the tour clears `activeStepId` and the spotlight, and **leaves the view exactly where it
is**. It does not restore whatever was on screen before the tour started: mid-presentation, snapping
the panes back to a previous state is more surprising than staying put.

The bar is always visible, in mock mode and against a real backend. It is a product feature — a
guided tour — not a debug affordance.

## 7. Error handling

The tour has no I/O, so there is nothing to fail asynchronously. Two degenerate cases:

- **Unknown hash** (`#/demo/nope`) — ignored, tour stays off, no error shown.
- **Step 5 finds no active `RESEARCHER`** — the step applies its Trace state normally and simply does
  not open the inspector. Presenting must never hard-fail on stage.

`Workspace` already wraps each pane in a `PaneBoundary`, so a throw inside a spotlighted subtree
degrades to that pane's boundary rather than a blank app.

## 8. Testing

- `applyDemoStep` — table-driven unit test: for each of the six steps, apply it to a fresh store and
  assert the resulting `useUiStore` and `useDemoStore` snapshots. This is the test that would catch a
  step silently pointing at a tab that no longer exists.
- `applyDemoStep` teardown — applying step 5 then step 1 leaves `inspectorAgentId === null`.
- Step 5 fallback — with no active `RESEARCHER`, the Trace state still applies and no inspector opens.
- Hash round-trip — `parseDemoHash` / `formatDemoHash` are pure and unit-tested; unknown ids return
  `null`.
- The six lifted `useState` → store changes are mechanical and covered by `tsc -b` and `eslint`.

## 9. Out of scope

- Triggering mock events from the tour (posting the attack message, answering the discount card).
  Explicitly rejected: it would mutate the world and make steps single-use.
- A separate presenter view, speaker notes, or auto-advance.
- Any change to the mock scenario timeline.
- The two UI gaps the demo script calls out (a finance summary page; live PM ticket-splitting). They
  are real gaps but separate work.
