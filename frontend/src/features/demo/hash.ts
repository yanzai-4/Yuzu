import { stepById, type DemoStepId } from './steps';

const PREFIX = '#/demo/';

/** v0.0.26 🍊 The step id in a location hash, or null when there is no valid one. */
export function parseDemoHash(hash: string): DemoStepId | null {
  if (!hash.startsWith(PREFIX)) return null;
  return stepById(hash.slice(PREFIX.length))?.id ?? null;
}

/** v0.0.26 🍊 The location hash for a step. */
export function formatDemoHash(id: DemoStepId): string {
  return `${PREFIX}${id}`;
}
