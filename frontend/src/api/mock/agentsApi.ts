import { CITRUS_NAMES, FRUIT_COLORS, avatarKeyFromName } from '../../lib/citrus';
import type { YuzuApi } from '../client';
import type { Agent } from '../types';
import type { MockContext } from './context';
import { DEMO_AGENTS, ROLE_PRESETS, demoAgent, demoWorkingMemory, presetOf } from './seedAgents';
import { clone, hex, nowText, sleep } from './util';

const MAX_AGENTS = 8;

type AgentsApi = Pick<
  YuzuApi,
  | 'listRoles'
  | 'listAgents'
  | 'createAgent'
  | 'updateAgent'
  | 'retireAgent'
  | 'pauseAgent'
  | 'resumeAgent'
  | 'interruptAgent'
  | 'getWorkingMemory'
  | 'getAgentTasks'
  | 'listAgentEvents'
  | 'seedDemo'
>;

/** v0.0.4 🍊 Mock agent endpoints: roles, hire (max 8), edit, retire, pause/resume/interrupt, memory. */
export function createAgentsApi(ctx: MockContext): AgentsApi {
  const { world } = ctx;
  const { server } = world;
  const { state } = server;
  const usedNames = new Set([...state.agents.values()].map((a) => a.name));

  const onboard = (agent: Agent) => {
    state.agents.set(agent.agentId, agent);
    usedNames.add(agent.name);
    state.workingMemory.set(agent.agentId, { agentId: agent.agentId, digest: null, entries: [] });
    server.publish('agent.upsert', agent, agent.agentId);
    world.setStatus(agent.agentId, 'IDLE', 'SYSTEM', 'Just joined — setting up my desk', 0, 0);
    world.publishTaskList({ agentId: agent.agentId, current: null, recentArchived: [] });
    world.postSystem(`${agent.name} joined as ${agent.title}.`);
  };

  return {
    async listRoles() {
      await ctx.latency();
      return clone(ROLE_PRESETS);
    },

    async listAgents() {
      await ctx.latency();
      return clone([...state.agents.values()]);
    },

    async createAgent(roomId, body) {
      const path = `/api/rooms/${roomId}/agents`;
      await ctx.latency();
      if (state.agents.size >= MAX_AGENTS) {
        throw ctx.fail('POST', path, 'AGENT_LIMIT', 'A workgroup can have at most 8 agents.', { max: MAX_AGENTS, current: state.agents.size });
      }
      const name = CITRUS_NAMES.find((n) => !usedNames.has(n)) ?? `Citrus ${usedNames.size + 1}`;
      const preset = presetOf(body.role);
      const key = avatarKeyFromName(name);
      const agent: Agent = {
        agentId: `agent-${hex(4)}`,
        roomId,
        name,
        avatarKey: key,
        color: FRUIT_COLORS[key] ?? '#ff9f1c',
        role: body.role,
        title: body.title?.trim() || preset.title,
        scopeText: body.scopeText?.trim() || preset.scopeText,
        persona: body.persona?.trim() || `A friendly ${preset.title.toLowerCase()} who explains every decision.`,
        permissions: body.permissions ?? preset.permissions,
        limits: { ...preset.limits, ...body.limits },
        state: 'ACTIVE',
        createdTime: nowText(),
      };
      onboard(agent);
      void sleep(1800).then(() =>
        world.streamMessage(agent.agentId, `Hi everyone! I'm ${name}, your new ${agent.title.toLowerCase()}. ${agent.scopeText} Mention me with @${name} when you need me.`),
      );
      return clone(agent);
    },

    async updateAgent(agentId, body) {
      const path = `/api/agents/${agentId}`;
      await ctx.latency();
      const agent = ctx.agent('PATCH', path, agentId);
      if (body.title !== undefined && !body.title.trim()) throw ctx.fail('PATCH', path, 'BAD_REQUEST', 'Title must not be empty.', { fields: { title: 'must not be empty' } });
      const next: Agent = {
        ...agent,
        title: body.title?.trim() ?? agent.title,
        scopeText: body.scopeText ?? agent.scopeText,
        persona: body.persona ?? agent.persona,
        permissions: body.permissions ?? agent.permissions,
        limits: { ...agent.limits, ...body.limits },
      };
      state.agents.set(agentId, next);
      server.publish('agent.upsert', next, agentId);
      return clone(next);
    },

    async retireAgent(agentId) {
      await ctx.latency();
      const agent = ctx.agent('DELETE', `/api/agents/${agentId}`, agentId);
      world.interrupt(agentId);
      state.agents.delete(agentId);
      state.statuses.delete(agentId);
      state.taskLists.delete(agentId);
      server.publish('agent.removed', { agentId }, agentId);
      world.postSystem(`${agent.name} retired. Thanks for all the zest!`);
    },

    async pauseAgent(agentId) {
      await ctx.latency();
      const agent = ctx.agent('POST', `/api/agents/${agentId}/pause`, agentId);
      world.interrupt(agentId);
      const next: Agent = { ...agent, state: 'PAUSED' };
      state.agents.set(agentId, next);
      server.publish('agent.upsert', next, agentId);
      world.setStatus(agentId, 'PAUSED', 'SYSTEM', 'Paused — not reading new messages', 0, 0);
      return clone(state.statuses.get(agentId)!);
    },

    async resumeAgent(agentId) {
      await ctx.latency();
      const agent = ctx.agent('POST', `/api/agents/${agentId}/resume`, agentId);
      const next: Agent = { ...agent, state: 'ACTIVE' };
      state.agents.set(agentId, next);
      server.publish('agent.upsert', next, agentId);
      world.setStatus(agentId, 'IDLE', 'SYSTEM', 'Back at my desk', 0, 0);
      return clone(state.statuses.get(agentId)!);
    },

    async interruptAgent(agentId) {
      await ctx.latency();
      const agent = ctx.agent('POST', `/api/agents/${agentId}/interrupt`, agentId);
      world.interrupt(agentId);
      if (agent.state === 'ACTIVE') world.setStatus(agentId, 'IDLE', 'MAIN', 'Interrupted — waiting for new input', 0, 0);
      return clone(state.statuses.get(agentId)!);
    },

    async getWorkingMemory(agentId) {
      await ctx.latency();
      ctx.agent('GET', `/api/agents/${agentId}/working-memory`, agentId);
      return clone(state.workingMemory.get(agentId) ?? { agentId, digest: null, entries: [] });
    },

    async getAgentTasks(agentId) {
      await ctx.latency();
      ctx.agent('GET', `/api/agents/${agentId}/tasks`, agentId);
      return clone(state.taskLists.get(agentId) ?? { agentId, current: null, recentArchived: [] });
    },

    async listAgentEvents(agentId, _beforeSeq, limit = 100) {
      await ctx.latency();
      ctx.agent('GET', `/api/agents/${agentId}/events`, agentId);
      return clone(state.events.filter((e) => e.agentId === agentId).slice(-limit));
    },

    async seedDemo(roomId) {
      await ctx.latency();
      for (const spec of DEMO_AGENTS) {
        if (state.agents.size >= MAX_AGENTS) break;
        if ([...state.agents.values()].some((a) => a.name === spec.name)) continue;
        const agent = demoAgent(spec, roomId, 0);
        onboard(agent);
        state.workingMemory.set(agent.agentId, demoWorkingMemory(agent.agentId, agent.name));
      }
      return clone([...state.agents.values()]);
    },
  };
}
