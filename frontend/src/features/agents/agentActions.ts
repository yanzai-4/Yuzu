import {
  createAgent,
  interruptAgent,
  pauseAgent,
  resumeAgent,
  retireAgent,
  seedDemo,
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

/** v0.0.4 🍊 Pauses, resumes or interrupts an agent and applies the returned desk status. */
export async function controlAgent(agentId: string, action: 'pause' | 'resume' | 'interrupt'): Promise<boolean> {
  const call = action === 'pause' ? pauseAgent : action === 'resume' ? resumeAgent : interruptAgent;
  try {
    const status = await call(agentId);
    applyLocal('agent.status', status, agentId);
    const agent = useRoomStore.getState().agents[agentId];
    if (agent && action !== 'interrupt') {
      applyLocal('agent.upsert', { ...agent, state: action === 'pause' ? 'PAUSED' : 'ACTIVE' }, agentId);
    }
    return true;
  } catch {
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
