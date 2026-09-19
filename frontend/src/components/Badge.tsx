import clsx from 'clsx';
import type { CSSProperties, ReactNode } from 'react';
import { moduleHue, PHASE_HUES } from '../lib/colors';
import { humanizeEnum } from '../lib/format';
import type { EventPhase } from '../api/types';

/** v0.0.4 🍊 Semantic tones of the Badge. */
export type BadgeTone = 'neutral' | 'accent' | 'leaf' | 'warn' | 'danger' | 'info' | 'ok';

const TONES: Record<BadgeTone, string> = {
  neutral: 'bg-surface-3 text-ink-2 border-line',
  accent: 'bg-accent-soft text-accent-soft-ink border-accent/30',
  leaf: 'bg-leaf-soft text-leaf-ink border-leaf/30',
  warn: 'bg-warn-bg text-warn-ink border-warn-line',
  danger: 'bg-danger-soft text-danger-ink border-danger/40',
  info: 'bg-info-soft text-info border-info/30',
  ok: 'bg-ok-soft text-ok border-ok/30',
};

/** v0.0.4 🍊 Small rounded label with a semantic tone. */
export function Badge({
  tone = 'neutral',
  children,
  className,
  title,
}: {
  tone?: BadgeTone;
  children: ReactNode;
  className?: string;
  title?: string;
}) {
  return (
    <span
      title={title}
      className={clsx(
        'inline-flex shrink-0 items-center gap-1 rounded-full border px-1.5 py-px text-[10px] leading-4 font-semibold tracking-wide uppercase',
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

/** v0.0.4 🍊 Chip tinted by an arbitrary hue (modules, phases, desk states). */
export function HueChip({
  hue,
  children,
  className,
  title,
}: {
  hue: string;
  children: ReactNode;
  className?: string;
  title?: string;
}) {
  return (
    <span
      title={title}
      style={{ '--hue': hue } as CSSProperties}
      className={clsx(
        'hue-chip inline-flex max-w-full shrink-0 items-center truncate rounded-md px-1.5 py-px font-mono text-[10px] leading-4 font-semibold',
        className,
      )}
    >
      {children}
    </span>
  );
}

/** v0.0.4 🍊 Chip showing an agent module name in its hue. */
export function ModuleChip({ module, className }: { module: string; className?: string }) {
  return (
    <HueChip hue={moduleHue(module)} className={className} title={`Module: ${humanizeEnum(module)}`}>
      {module}
    </HueChip>
  );
}

/** v0.0.4 🍊 Chip showing a trace phase in its hue. */
export function PhaseChip({ phase }: { phase: EventPhase }) {
  return <HueChip hue={PHASE_HUES[phase] ?? '#64748b'}>{phase}</HueChip>;
}
