import { create } from 'zustand';

/** v0.0.4 🍊 The three panes (shown as tabs below the desktop breakpoint). */
export type Pane = 'chat' | 'office' | 'insights';
/** v0.0.4 🍊 Tabs of the insights pane. */
export type InsightsTab = 'tasks' | 'usage' | 'sim' | 'trace';
/** v0.0.26 🍊 Sub-tabs of the Tasks insights tab. */
export type TasksView = 'lists' | 'board';
/** v0.0.26 🍊 Sub-tabs of the Sim insights tab. */
export type SimView = 'emails' | 'trades' | 'portfolios';
/** v0.0.26 🍊 Sub-tabs of the Trace insights tab. */
export type TraceView = 'events' | 'incidents' | 'errors';
/** v0.0.26 🍊 Breakdown dimension of the Usage tab. */
export type UsageDimension = 'agent' | 'module' | 'tier' | 'model';
/** v0.0.4 🍊 Filters of the live event list ('ALL' = no filter). */
export interface TraceFilter {
  agentId: string;
  module: string;
  phase: string;
}

/** v0.0.4 🍊 No filter at all. */
export const NO_FILTER: TraceFilter = { agentId: 'ALL', module: 'ALL', phase: 'ALL' };
/** v0.0.4 🍊 Which modal dialog is open. */
export type DialogState = { kind: 'coworkers'; view: 'roster' | 'hire' } | { kind: 'console' } | null;

/** v0.0.4 🍊 A transient notification. */
export interface Toast {
  id: number;
  tone: 'error' | 'success' | 'info' | 'warning';
  title: string;
  message?: string;
  /** Error code chip (errors only). */
  code?: string;
}

/** v0.0.4 🍊 Shape of the UI store. */
export interface UiState {
  dialog: DialogState;
  inspectorAgentId: string | null;
  /** Trace whose waterfall is open. */
  traceId: string | null;
  mobilePane: Pane;
  insightsTab: InsightsTab;
  /** Sub-tab of the Tasks tab. */
  tasksView: TasksView;
  /** Sub-tab of the Sim tab. */
  simView: SimView;
  /** Sub-tab of the Trace tab. */
  traceView: TraceView;
  /** Breakdown dimension of the Usage tab. */
  usageDimension: UsageDimension;
  /** Agent / module / phase filter of the live event stream. */
  traceFilter: TraceFilter;
  toasts: Toast[];
  /** Chat messages already seen in the narrow layout (drives the unread badge). */
  chatSeen: number;
  /** Role preselected when the hire form opens from an empty desk. */
  hireRole: string | null;
}

const MAX_TOASTS = 5;
let toastSeq = 0;

/** v0.0.4 🍊 Dialogs, drawer, tabs and toasts. */
export const useUiStore = create<UiState>()(() => ({
  dialog: null,
  inspectorAgentId: null,
  traceId: null,
  mobilePane: 'chat',
  insightsTab: 'tasks',
  tasksView: 'lists',
  simView: 'emails',
  traceView: 'events',
  usageDimension: 'agent',
  traceFilter: NO_FILTER,
  toasts: [],
  chatSeen: 0,
  hireRole: null,
}));

/** v0.0.4 🍊 Records how many chat messages the user has seen (narrow layout badge). */
export function markChatSeen(count: number): void {
  if (useUiStore.getState().chatSeen !== count) useUiStore.setState({ chatSeen: count });
}

/** v0.0.4 🍊 Opens a dialog (closing any other). */
export function openDialog(dialog: Exclude<DialogState, null>): void {
  useUiStore.setState({ dialog });
}

/** v0.0.4 🍊 Opens the Coworkers dialog on the hire form (optionally with a role preselected). */
export function openHireDialog(role: string | null = null): void {
  useUiStore.setState({ dialog: { kind: 'coworkers', view: 'hire' }, hireRole: role, inspectorAgentId: null });
}

/** v0.0.4 🍊 Closes the open dialog. */
export function closeDialog(): void {
  useUiStore.setState({ dialog: null });
}

/** v0.0.4 🍊 Opens the Agent Inspector drawer for an agent. */
export function openInspector(agentId: string): void {
  useUiStore.setState({ inspectorAgentId: agentId, dialog: null });
}

/** v0.0.4 🍊 Closes the Agent Inspector drawer. */
export function closeInspector(): void {
  useUiStore.setState({ inspectorAgentId: null });
}

/** v0.0.4 🍊 Opens the waterfall of one trace. */
export function openTrace(traceId: string): void {
  useUiStore.setState({ traceId });
}

/** v0.0.4 🍊 Closes the trace waterfall. */
export function closeTrace(): void {
  useUiStore.setState({ traceId: null });
}

/** v0.0.4 🍊 Switches the pane shown in the narrow (tabbed) layout. */
export function setMobilePane(mobilePane: Pane): void {
  useUiStore.setState({ mobilePane });
}

/** v0.0.4 🍊 Switches the insights tab. */
export function setInsightsTab(insightsTab: InsightsTab): void {
  useUiStore.setState({ insightsTab });
}

/** v0.0.4 🍊 Shows a toast (identical visible toasts are not repeated; beyond five the oldest go). */
export function pushToast(toast: Omit<Toast, 'id'>): number {
  const duplicate = useUiStore
    .getState()
    .toasts.find((t) => t.title === toast.title && t.message === toast.message && t.code === toast.code);
  if (duplicate) return duplicate.id;
  const id = ++toastSeq;
  useUiStore.setState((s) => ({ toasts: [...s.toasts, { ...toast, id }].slice(-MAX_TOASTS) }));
  return id;
}

/** v0.0.4 🍊 Removes a toast. */
export function dismissToast(id: number): void {
  useUiStore.setState((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
}

/** v0.0.26 🍊 Switches the Tasks sub-tab. */
export function setTasksView(tasksView: TasksView): void {
  useUiStore.setState({ tasksView });
}

/** v0.0.26 🍊 Switches the Sim sub-tab. */
export function setSimView(simView: SimView): void {
  useUiStore.setState({ simView });
}

/** v0.0.26 🍊 Switches the Trace sub-tab. */
export function setTraceView(traceView: TraceView): void {
  useUiStore.setState({ traceView });
}

/** v0.0.26 🍊 Switches the Usage breakdown dimension. */
export function setUsageDimension(usageDimension: UsageDimension): void {
  useUiStore.setState({ usageDimension });
}

/** v0.0.26 🍊 Sets the live event stream filter. */
export function setTraceFilter(traceFilter: TraceFilter): void {
  useUiStore.setState({ traceFilter });
}
