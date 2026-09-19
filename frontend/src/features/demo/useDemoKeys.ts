import { useEffect } from 'react';
import { useDemoStore } from '../../stores/demo';
import { applyDemoStep, exitDemo } from './applyDemoStep';
import { formatDemoHash, parseDemoHash } from './hash';
import { DEMO_STEPS, stepById } from './steps';

/** v0.0.33 🍊 True while the user is typing, so the tour never eats their keystrokes. */
function typing(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  if (!el) return false;
  const tag = el.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable;
}

/** v0.0.33 🍊 Number-key shortcuts (1-6 select, 0/Escape exit) and two-way `#/demo/<id>` sync. */
export function useDemoKeys(): void {
  // Apply the step named by the hash on load and whenever the hash changes.
  useEffect(() => {
    const fromHash = () => {
      const id = parseDemoHash(window.location.hash);
      if (id === useDemoStore.getState().activeStepId) return;
      const step = id ? stepById(id) : null;
      if (step) applyDemoStep(step);
      else exitDemo();
    };
    fromHash();
    window.addEventListener('hashchange', fromHash);
    return () => window.removeEventListener('hashchange', fromHash);
  }, []);

  // Write the active step back into the hash.
  useEffect(
    () =>
      useDemoStore.subscribe((state) => {
        const want = state.activeStepId ? formatDemoHash(state.activeStepId) : '';
        if (window.location.hash === want) return;
        if (want) window.location.hash = want;
        else history.replaceState(null, '', `${window.location.pathname}${window.location.search}`);
      }),
    [],
  );

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.metaKey || event.ctrlKey || event.altKey || typing(event.target)) return;
      if (event.key === 'Escape' || event.key === '0') {
        if (useDemoStore.getState().activeStepId) {
          event.preventDefault();
          exitDemo();
        }
        return;
      }
      const step = DEMO_STEPS.find((s) => String(s.n) === event.key);
      if (!step) return;
      event.preventDefault();
      applyDemoStep(step);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);
}
