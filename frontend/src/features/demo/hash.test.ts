import { describe, expect, it } from 'vitest';
import { formatDemoHash, parseDemoHash } from './hash';
import { DEMO_STEPS } from './steps';

describe('parseDemoHash', () => {
  it('reads a known step id', () => {
    expect(parseDemoHash('#/demo/safety')).toBe('safety');
  });

  it('ignores an unknown step id', () => {
    expect(parseDemoHash('#/demo/nope')).toBeNull();
  });

  it('ignores an unrelated hash', () => {
    expect(parseDemoHash('#/settings')).toBeNull();
  });

  it('ignores an empty hash', () => {
    expect(parseDemoHash('')).toBeNull();
  });

  it('round-trips every step', () => {
    for (const step of DEMO_STEPS) {
      expect(parseDemoHash(formatDemoHash(step.id))).toBe(step.id);
    }
  });
});

describe('DEMO_STEPS', () => {
  it('has six steps numbered 1 to 6 in order', () => {
    expect(DEMO_STEPS.map((s) => s.n)).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('has unique ids', () => {
    expect(new Set(DEMO_STEPS.map((s) => s.id)).size).toBe(6);
  });
});
