import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** v0.0.4 🍊 Theme preference: follow the OS or force one. */
export type ThemePref = 'system' | 'light' | 'dark';

/** v0.0.4 🍊 Shape of the preferences store. */
export interface PrefsState {
  theme: ThemePref;
}

/** v0.0.4 🍊 Per-browser preferences persisted in localStorage (`yuzu.prefs`). */
export const usePrefsStore = create<PrefsState>()(
  persist(() => ({ theme: 'system' as ThemePref }), { name: 'yuzu.prefs', version: 1 }),
);

/** v0.0.4 🍊 Sets the theme preference. */
export function setThemePref(theme: ThemePref): void {
  usePrefsStore.setState({ theme });
}

/** v0.0.4 🍊 Mirrors the theme preference onto <html data-theme> (CSS variables switch on it). */
export function syncThemeAttribute(): () => void {
  const apply = (theme: ThemePref) => {
    if (theme === 'system') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', theme);
  };
  apply(usePrefsStore.getState().theme);
  return usePrefsStore.subscribe((state) => apply(state.theme));
}
