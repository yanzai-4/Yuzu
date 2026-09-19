import type { YuzuBackend } from '../client';
import { createAgentsApi } from './agentsApi';
import { createChatApi } from './chatApi';
import { createMockContext } from './context';
import { Reactions } from './reactions';
import { createSeedState } from './seed';
import { MockServer } from './server';
import { createSettingsApi } from './settingsApi';
import { Simulator } from './simulator';
import { MockWorld } from './world';

/**
 * v0.0.4 🍊 Creates the in-memory mock backend (VITE_MOCK=1): the same endpoint functions as the HTTP
 * client plus a stream transport, backed by a seeded office that keeps working on its own.
 */
export function createMockBackend(): YuzuBackend {
  const server = new MockServer(createSeedState());
  const world = new MockWorld(server);
  const reactions = new Reactions(world);
  const simulator = new Simulator(world, reactions);
  const ctx = createMockContext(world, reactions);
  return {
    api: { ...createChatApi(ctx), ...createAgentsApi(ctx), ...createSettingsApi(ctx) },
    transport: {
      open(roomId, after, handlers) {
        // The office comes alive when the first client connects (not on the join screen).
        simulator.start();
        return server.connect(roomId, after, handlers);
      },
    },
  };
}
