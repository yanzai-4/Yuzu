/** v0.0.4 🍊 Small helpers of the mock backend: ids, randomness, delays and times. */
import { formatCompactTime } from '../../lib/time';

/** v0.0.4 🍊 Resolves after `ms` milliseconds. */
export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** v0.0.4 🍊 Random integer in [min, max]. */
export function randInt(min: number, max: number): number {
  return Math.floor(min + Math.random() * (max - min + 1));
}

/** v0.0.4 🍊 Random element of a non-empty array. */
export function pick<T>(items: readonly T[]): T {
  const item = items[Math.floor(Math.random() * items.length)];
  if (item === undefined) throw new Error('pick() on an empty array');
  return item;
}

/** v0.0.4 🍊 `n` random lowercase hex digits. */
export function hex(n: number): string {
  let out = '';
  for (let i = 0; i < n; i++) out += Math.floor(Math.random() * 16).toString(16);
  return out;
}

/** v0.0.4 🍊 Record id `<name>-<agentHex>-<10hex>` like the backend's IdGen. */
export function recordId(name: string, agentId?: string | null): string {
  const owner = agentId?.startsWith('agent-') ? agentId.slice(6) : '0000';
  return `${name}-${owner}-${hex(10)}`;
}

/** v0.0.4 🍊 Current time in the backend's compact natural form. */
export function nowText(): string {
  return formatCompactTime(new Date());
}

/** v0.0.4 🍊 A time `seconds` ago in the compact natural form (for seed data). */
export function agoText(seconds: number): string {
  return formatCompactTime(new Date(Date.now() - seconds * 1000));
}

/** v0.0.4 🍊 Deep copy so stores never share mutable objects with the mock server. */
export function clone<T>(value: T): T {
  return structuredClone(value);
}

/** v0.0.4 🍊 Splits text into small word chunks for streamed replies. */
export function chunkWords(text: string): string[] {
  const words = text.match(/\S+\s*/g) ?? [text];
  const chunks: string[] = [];
  for (let i = 0; i < words.length; ) {
    const size = randInt(1, 3);
    chunks.push(words.slice(i, i + size).join(''));
    i += size;
  }
  return chunks;
}
