/** v0.0.4 🍊 Number and text formatting helpers shared by every pane. */

const intFormat = new Intl.NumberFormat('en-US');
const usdFormat = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const usdWholeFormat = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
});
const qtyFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 });

/** v0.0.4 🍊 "12,345". */
export function formatInt(n: number | null | undefined): string {
  return n == null || Number.isNaN(n) ? '—' : intFormat.format(Math.round(n));
}

/** v0.0.4 🍊 Short token counts: 950 → "950", 12_345 → "12.3k", 4_560_000 → "4.56M". */
export function formatCompact(n: number | null | undefined): string {
  if (n == null || Number.isNaN(n)) return '—';
  const abs = Math.abs(n);
  if (abs < 1000) return String(Math.round(n));
  if (abs < 1_000_000) return `${trim(n / 1000, abs < 10_000 ? 2 : 1)}k`;
  if (abs < 1_000_000_000) return `${trim(n / 1_000_000, abs < 10_000_000 ? 2 : 1)}M`;
  return `${trim(n / 1_000_000_000, 2)}B`;
}

/** v0.0.4 🍊 A ratio (0..1) as a percentage, or "n/a" when unknown. */
export function formatPercent(ratio: number | null | undefined, digits = 1): string {
  if (ratio == null || Number.isNaN(ratio)) return 'n/a';
  return `${(ratio * 100).toFixed(digits)}%`;
}

/** v0.0.4 🍊 "$12,345.67". */
export function formatUsd(n: number | null | undefined): string {
  return n == null || Number.isNaN(n) ? '—' : usdFormat.format(n);
}

/** v0.0.4 🍊 "$12,346" (no cents; for large amounts). */
export function formatUsdWhole(n: number | null | undefined): string {
  return n == null || Number.isNaN(n) ? '—' : usdWholeFormat.format(n);
}

/** v0.0.4 🍊 Share quantities with up to four decimals. */
export function formatQty(n: number | null | undefined): string {
  return n == null || Number.isNaN(n) ? '—' : qtyFormat.format(n);
}

/** v0.0.4 🍊 "IN_PROGRESS" → "In progress". */
export function humanizeEnum(value: string): string {
  const text = value.replace(/_/g, ' ').toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** v0.0.4 🍊 Cuts text at a word boundary and appends an ellipsis. */
export function truncate(text: string, max: number): string {
  const clean = text.replace(/\s+/g, ' ').trim();
  if (clean.length <= max) return clean;
  const cut = clean.slice(0, max - 1);
  const space = cut.lastIndexOf(' ');
  return `${(space > max * 0.6 ? cut.slice(0, space) : cut).replace(/[\s,.;:]+$/, '')}…`;
}

/** v0.0.4 🍊 One or two uppercase initials for a display name. */
export function initials(name: string): string {
  const parts = name.trim().split(/[\s._-]+/).filter(Boolean);
  const first = parts[0]?.charAt(0) ?? '?';
  const second = parts.length > 1 ? (parts[1]?.charAt(0) ?? '') : (parts[0]?.charAt(1) ?? '');
  return (first + second).toUpperCase();
}

/** v0.0.4 🍊 "1 agent" / "3 agents". */
export function plural(count: number, word: string, pluralWord = `${word}s`): string {
  return `${count} ${count === 1 ? word : pluralWord}`;
}

function trim(value: number, digits: number): string {
  return value.toFixed(digits).replace(/\.?0+$/, '');
}
