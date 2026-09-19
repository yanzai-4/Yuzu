import { failRequest, localApiError, type ApiRequestError, type HttpMethod } from '../http';
import type { Agent, ErrorCode, User } from '../types';
import type { Reactions } from './reactions';
import { randInt, sleep } from './util';
import type { MockWorld } from './world';

/** v0.0.4 🍊 HTTP status the real backend uses for each error code. */
const STATUS: Partial<Record<ErrorCode, number>> = {
  BAD_REQUEST: 400,
  NOT_FOUND: 404,
  CONFLICT: 409,
  AGENT_LIMIT: 409,
  PERMISSION_DENIED: 403,
  NOT_CONFIGURED: 412,
};

/** v0.0.4 🍊 Everything the mock endpoint implementations share. */
export interface MockContext {
  world: MockWorld;
  reactions: Reactions;
  /** Simulated network latency. */
  latency: () => Promise<void>;
  /** Builds (and reports) an ApiRequestError exactly like a failed HTTP call. */
  fail: (method: HttpMethod, path: string, code: ErrorCode, message: string, details?: Record<string, unknown>) => ApiRequestError;
  /** The agent or a NOT_FOUND error. */
  agent: (method: HttpMethod, path: string, agentId: string) => Agent;
  /** The user or a NOT_FOUND error. */
  user: (method: HttpMethod, path: string, userId: string) => User;
}

/** v0.0.4 🍊 Creates the shared context of the mock endpoints. */
export function createMockContext(world: MockWorld, reactions: Reactions): MockContext {
  const fail: MockContext['fail'] = (method, path, code, message, details = {}) =>
    failRequest({ ...localApiError(code, message, details) }, STATUS[code] ?? 500, method, path);
  return {
    world,
    reactions,
    latency: () => sleep(randInt(60, 220)),
    fail,
    agent(method, path, agentId) {
      const agent = world.server.state.agents.get(agentId);
      if (!agent) throw fail(method, path, 'NOT_FOUND', `Agent ${agentId} does not exist.`, { agentId });
      return agent;
    },
    user(method, path, userId) {
      const user = world.server.state.users.get(userId);
      if (!user) throw fail(method, path, 'NOT_FOUND', 'Unknown user. Please join the workspace again.', { userId });
      return user;
    },
  };
}
