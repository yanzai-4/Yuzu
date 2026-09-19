import {
  createAgent,
  interruptAgent,
  pauseAgent,
  resumeAgent,
  resumeAllAgents,
  retireAgent,
  seedDemo,
  stopAllAgents,
  updateAgent,
} from '../../api/client';
import { ApiRequestError } from '../../api/http';
import type { Agent, ApiError, CreateAgentRequest, UpdateAgentRequest } from '../../api/types';
import { applyLocal } from '../../stores/applyEvent';
import { useRoomStore } from '../../stores/room';
import { closeInspector, pushToast } from '../../stores/ui';

/** v0.0.4 🍊 Result of an action whose error the caller wants to show inline. */
export type ActionResult<T> = { ok: true; value: T } | { ok: false; error: ApiError | null };

function failure<T>(e: unknown): ActionResult<T> {
  return { ok: false, error: e instanceof ApiRequestError ? e.apiError : null };
}

/** v0.0.4 🍊 Hires an agent (the backend picks the citrus name); upserts it locally on success. */
export async function hireAgent(body: CreateAgentRequest): Promise<ActionResult<Agent>> {
  try {
    const agent = await createAgent(useRoomStore.getState().roomId, body);
    applyLocal('agent.upsert', agent, agent.agentId);
    pushToast({ tone: 'success', title: `Welcome aboard, ${agent.name}!`, message: `${agent.name} joined as ${agent.title}.` });
    return { ok: true, value: agent };
  } catch (e) {
    return failure(e);
  }
}

/** v0.0.4 🍊 Edits an agent's profile, permissions or limits (PATCH). */
export async function patchAgent(agentId: string, body: UpdateAgentRequest): Promise<ActionResult<Agent>> {
  try {
    const agent = await updateAgent(agentId, body);
    applyLocal('agent.upsert', agent, agentId);
    return { ok: true, value: agent };
  } catch (e) {
    return failure(e);
  }
}

/**
 * v0.0.30 🍊 Pauses, resumes or interrupts an agent: the new state is applied optimistically (the desk
 * reacts instantly), the server's status replaces it on success, and a failure rolls the desk back. The
 * failure toast comes from the HTTP layer, which reports every failed request once.
 */
export async function controlAgent(agentId: string, action: 'pause' | 'resume' | 'interrupt'): Promise<boolean> {
  const call = action === 'pause' ? pauseAgent : action === 'resume' ? resumeAgent : interruptAgent;
  const before = useRoomStore.getState().agents[agentId];
  if (before && action !== 'interrupt') {
    applyLocal('agent.upsert', { ...before, state: action === 'pause' ? 'PAUSED' : 'ACTIVE' }, agentId);
  }
  try {
    const status = await call(agentId);
    applyLocal('agent.status', status, agentId);
    return true;
  } catch {
    if (before) applyLocal('agent.upsert', before, agentId);
    return false;
  }
}

/**
 * v0.0.30 🍊 Stops (or resumes) every coworker of the room at once, optimistically, and confirms with a
 * toast; a failure restores every agent the way it was.
 */
export async function controlRoom(action: 'stop-all' | 'resume-all'): Promise<boolean> {
  const roomId = useRoomStore.getState().roomId;
  const before = Object.values(useRoomStore.getState().agents);
  const nextState = action === 'stop-all' ? 'PAUSED' : 'ACTIVE';
  for (const agent of before) {
    if (agent.state !== 'RETIRED') applyLocal('agent.upsert', { ...agent, state: nextState }, agent.agentId);
  }
  try {
    const result = await (action === 'stop-all' ? stopAllAgents(roomId) : resumeAllAgents(roomId));
    for (const status of result.statuses) applyLocal('agent.status', status, status.agentId);
    pushToast({
      tone: action === 'stop-all' ? 'info' : 'success',
      title: action === 'stop-all' ? 'Everyone stopped' : 'Everyone is back',
      message:
        result.affected === 0
          ? 'Nobody had to change: the office was already like that.'
          : `${result.affected} coworker${result.affected === 1 ? '' : 's'} ${action === 'stop-all' ? 'stopped what they were doing.' : 'went back to work.'}`,
    });
    return true;
  } catch {
    for (const agent of before) applyLocal('agent.upsert', agent, agent.agentId);
    return false;
  }
}

/** v0.0.4 🍊 Retires an agent: removes it locally, closes the inspector and confirms with a toast. */
export async function retire(agentId: string): Promise<boolean> {
  const name = useRoomStore.getState().agents[agentId]?.name ?? 'The agent';
  try {
    await retireAgent(agentId);
    applyLocal('agent.removed', { agentId }, agentId);
    closeInspector();
    pushToast({ tone: 'info', title: `${name} retired`, message: 'Their desk is free for a new coworker.' });
    return true;
  } catch {
    return false;
  }
}

/** v0.0.4 🍊 Creates the four demo agents (POST /api/demo/seed). */
export async function seedDemoTeam(): Promise<boolean> {
  try {
    const agents = await seedDemo(useRoomStore.getState().roomId);
    for (const agent of agents) applyLocal('agent.upsert', agent, agent.agentId);
    pushToast({ tone: 'success', title: 'Demo team is here', message: 'Yuzu, Lime, Kumquat and Pomelo took their desks.' });
    return true;
  } catch {
    return false;
  }
}
