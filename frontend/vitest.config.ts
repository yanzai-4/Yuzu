import { defineConfig } from 'vitest/config';

/** v0.0.26 🍊 Vitest config: node environment, unit tests next to the code they cover. */
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
