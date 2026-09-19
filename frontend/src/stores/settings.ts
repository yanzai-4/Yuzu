import { create } from 'zustand';
import type { LlmSettingsView } from '../api/types';

/** v0.0.4 🍊 Shape of the settings store. */
export interface SettingsState {
  settings: LlmSettingsView | null;
  /** Model ids fetched from the provider (datalist suggestions). */
  models: string[];
}

/** v0.0.4 🍊 LLM console settings (bootstrap + `settings.changed`) and fetched model ids. */
export const useSettingsStore = create<SettingsState>()(() => ({ settings: null, models: [] }));

/** v0.0.4 🍊 Replaces the settings view. */
export function setLlmSettings(settings: LlmSettingsView): void {
  useSettingsStore.setState({ settings });
}

/** v0.0.4 🍊 Stores the provider's model ids (sorted, unique). */
export function setModels(models: string[]): void {
  useSettingsStore.setState({ models: [...new Set(models)].sort() });
}
