import type { DeskState, EventPhase, ModuleKind } from '../api/types';

/**
 * v0.0.4 🍊 Color helpers and the categorical hues used for modules, phases and desk states.
 *
 * Hues are mid-lightness so the `hue-chip` utility (tinted background + hue-mixed ink) reads well in
 * both the light and the dark theme.
 */

/** v0.0.4 🍊 Hue of each agent module (bubble chips, trace rows). */
export const MODULE_HUES: Record<ModuleKind, string> = {
  CHAT: '#3b82f6',
  SAFETY: '#e11d48',
  BEHAVIOR: '#a855f7',
  HIGH_RISK: '#dc2626',
  TOOL_CALLING: '#0d9488',
  MONITOR: '#64748b',
  MAIN: '#ea580c',
  PLANNING: '#16a34a',
  COGNITION: '#6366f1',
  SUBCONSCIOUS: '#db2777',
  LEARNING: '#65a30d',
  MEMORY: '#0891b2',
  WM_COMPACTOR: '#a16207',
  TOOL: '#0f766e',
  SYSTEM: '#78716c',
};

/** v0.0.4 🍊 Hue of each trace phase. */
export const PHASE_HUES: Record<EventPhase, string> = {
  START: '#3b82f6',
  STATE: '#64748b',
  END: '#16a34a',
  ERROR: '#dc2626',
  CANCELLED: '#d97706',
  INFO: '#8b5cf6',
};

/** v0.0.4 🍊 Label and dot color of each desk state. */
export const DESK_STATE_META: Record<DeskState, { label: string; color: string }> = {
  IDLE: { label: 'Idle', color: '#a3a3a3' },
  WORKING: { label: 'Working', color: '#16a34a' },
  THINKING: { label: 'Thinking', color: '#8b5cf6' },
  TALKING: { label: 'Talking', color: '#0ea5e9' },
  WAITING: { label: 'Waiting', color: '#f59e0b' },
  PAUSED: { label: 'Paused', color: '#78716c' },
  ERROR: { label: 'Error', color: '#dc2626' },
};

/** v0.0.4 🍊 Hue for a module name that may not be a known ModuleKind (bubble.module is free text). */
export function moduleHue(module: string | null | undefined): string {
  if (!module) return '#78716c';
  const key = module.toUpperCase().replace(/[\s-]+/g, '_') as ModuleKind;
  return MODULE_HUES[key] ?? '#78716c';
}

/** v0.0.4 🍊 Parses "#rgb" / "#rrggbb" into channels (fallback: citrus orange). */
export function hexToRgb(hex: string): [number, number, number] {
  let h = hex.trim().replace('#', '');
  if (h.length === 3) h = h.replace(/./g, (c) => c + c);
  const n = Number.parseInt(h.slice(0, 6), 16);
  if (h.length < 6 || Number.isNaN(n)) return [245, 158, 11];
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** v0.0.4 🍊 Mixes a color toward black (amount 0..1). */
export function shade(hex: string, amount: number): string {
  const [r, g, b] = hexToRgb(hex);
  return toHex(r * (1 - amount), g * (1 - amount), b * (1 - amount));
}

/** v0.0.4 🍊 Mixes a color toward white (amount 0..1). */
export function tint(hex: string, amount: number): string {
  const [r, g, b] = hexToRgb(hex);
  return toHex(r + (255 - r) * amount, g + (255 - g) * amount, b + (255 - b) * amount);
}

/** v0.0.4 🍊 Relative luminance (WCAG) of a hex color. */
export function luminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** v0.0.4 🍊 Dark or light text color that stays readable on the given background. */
export function readableInk(background: string): string {
  return luminance(background) > 0.4 ? '#2a1f14' : '#fffaf2';
}

/** v0.0.4 🍊 Stable pleasant color for a name (used when a human has no color yet). */
export function colorFromName(name: string): string {
  const palette = ['#e76f51', '#2a9d8f', '#e9c46a', '#8ab17d', '#6d597a', '#457b9d', '#f4a261', '#b56576'];
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) | 0;
  return palette[Math.abs(hash) % palette.length] ?? '#e76f51';
}

function toHex(r: number, g: number, b: number): string {
  const c = (v: number) =>
    Math.max(0, Math.min(255, Math.round(v)))
      .toString(16)
      .padStart(2, '0');
  return `#${c(r)}${c(g)}${c(b)}`;
}
