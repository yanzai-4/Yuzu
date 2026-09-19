import clsx from 'clsx';
import { useDemoStore } from '../../stores/demo';
import { applyDemoStep, exitDemo } from './applyDemoStep';
import { DEMO_STEPS } from './steps';

/** v0.0.33 🍊 The guided tour: a row of numbered chips and the caption of the active step. */
export function DemoBar() {
  const activeId = useDemoStore((s) => s.activeStepId);
  const active = DEMO_STEPS.find((s) => s.id === activeId) ?? null;
  return (
    // z-[35] keeps the chips clickable above the inspector drawer (z-30) and below the modals (z-40).
    <div className="relative z-[35] shrink-0 border-b border-line bg-surface-2 px-3 py-1.5">
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="mr-1 text-[10px] font-bold tracking-wider text-ink-3 uppercase">Tour</span>
        {DEMO_STEPS.map((step) => {
          const on = step.id === activeId;
          return (
            <button
              key={step.id}
              type="button"
              aria-pressed={on}
              onClick={() => (on ? exitDemo() : applyDemoStep(step))}
              className={clsx(
                'rounded-full px-2.5 py-1 text-xs font-semibold transition-colors',
                on ? 'bg-accent text-accent-ink' : 'bg-surface text-ink-2 hover:bg-surface-3',
              )}
            >
              <span className="mr-1 font-mono text-[10px] opacity-70">{step.n}</span>
              {step.label}
            </button>
          );
        })}
        {active ? (
          <button type="button" onClick={exitDemo} className="ml-auto text-[11px] text-ink-3 hover:underline">
            Exit tour (Esc)
          </button>
        ) : null}
      </div>
      {active ? <p className="mt-1 text-xs text-ink-2">{active.blurb}</p> : null}
    </div>
  );
}
