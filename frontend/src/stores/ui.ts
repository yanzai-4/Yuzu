import { create } from 'zustand';

/** v0.0.4 🍊 The three panes (shown as tabs below the desktop breakpoint). */
export type Pane = 'chat' | 'office' | 'insights';
/** v0.0.4 🍊 Tabs of the insights pane. */
export type InsightsTab = 'tasks' | 'usage' | 'sim' | 'trace';
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
  toasts: Toast[];
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
  toasts: [],
}));

/** v0.0.4 🍊 Opens a dialog (closing any other). */
export function openDialog(dialog: Exclude<DialogState, null>): void {
  useUiStore.setState({ dialog });
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

/** v0.0.4 🍊 Shows a toast (the oldest ones are dropped beyond five); returns its id. */
export function pushToast(toast: Omit<Toast, 'id'>): number {
  const id = ++toastSeq;
  useUiStore.setState((s) => ({ toasts: [...s.toasts, { ...toast, id }].slice(-MAX_TOASTS) }));
  return id;
}

/** v0.0.4 🍊 Removes a toast. */
export function dismissToast(id: number): void {
  useUiStore.setState((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
}
