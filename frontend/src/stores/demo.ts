import { create } from 'zustand';
import type { DemoStepId, SpotlightId } from '../features/demo/steps';

/** v0.0.33 🍊 Shape of the guided-tour store (presentation only; the app itself never reads it). */
export interface DemoState {
  /** null when the tour is not running. */
  activeStepId: DemoStepId | null;
  spotlight: ReadonlySet<SpotlightId>;
}

/** v0.0.33 🍊 Which tour step is active and which regions it highlights. */
export const useDemoStore = create<DemoState>()(() => ({
  activeStepId: null,
  spotlight: new Set<SpotlightId>(),
}));

/** v0.0.33 🍊 Whether a region is currently highlighted by the tour. */
export function useSpotlit(id: SpotlightId): boolean {
  return useDemoStore((s) => s.spotlight.has(id));
}
