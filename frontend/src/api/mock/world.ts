import { formatFullTime } from '../../lib/time';
import type { Agent, ChatMessage, DeskState, ErrorCode, ModuleKind, TaskListView, Ticket } from '../types';
import { recordLlmCall } from './llmCalls';
import type { MockServer } from './server';
import { TIER_MODELS, tierOf } from './usage';
import { chunkWords, hex, nowText, randInt, recordId, sleep } from './util';

interface OpenSpan {
  spanId: string;
  module: ModuleKind;
  /** TOOL spans run inside a TOOL_CALLING span that must be closed too. */
  outerSpanId?: string;
}

interface Run {
  traceId: string;
  rootSpanId: string;
  steps: number;
  open: OpenSpan | null;
}

/** v0.0.4 🍊 Options of an agent chat message. */
export interface AgentMessageOptions {
  kind?: ChatMessage['kind'];
  replyTo?: string | null;
  cardId?: string | null;
  mentionAll?: boolean;
}

/**
 * v0.0.4 🍊 Shared behaviors of the mock agents: desk statuses, chat messages (streamed or not),
 * nested trace spans, working memory, task lists and asynchronous errors.
 */
export class MockWorld {
  readonly server: MockServer;
  /** Agents currently streaming a message (the simulator leaves their status alone). */
  readonly busy = new Set<string>();
  private readonly runs = new Map<string, Run>();
  private readonly interrupted = new Set<string>();

  /** v0.0.4 🍊 Wraps the mock server. */
  constructor(server: MockServer) {
    this.server = server;
  }

  /** v0.0.4 🍊 An agent that can act right now (exists and is not paused). */
  activeAgent(agentId: string): Agent | null {
    const agent = this.server.state.agents.get(agentId);
    return agent && agent.state === 'ACTIVE' ? agent : null;
  }

  /** v0.0.4 🍊 Every agent that can act right now. */
  activeAgents(): Agent[] {
    return [...this.server.state.agents.values()].filter((a) => a.state === 'ACTIVE');
  }

  /** v0.0.4 🍊 Updates and publishes an agent's desk status. */
  setStatus(agentId: string, state: DeskState, module: string, summary: string, pool?: number, batches?: number): void {
    const prev = this.server.state.statuses.get(agentId);
    const status = {
      agentId,
      state,
      activeModules: state === 'IDLE' || state === 'PAUSED' ? [] : [module as ModuleKind],
      bubble: { module, summary },
      poolSize: pool ?? prev?.poolSize ?? 0,
      pendingBatches: batches ?? prev?.pendingBatches ?? 0,
      time: nowText(),
    };
    this.server.state.statuses.set(agentId, status);
    this.server.publish('agent.status', status, agentId);
  }

  /** v0.0.4 🍊 Ids of agents and humans mentioned as "@Name" in a text. */
  mentionsIn(text: string): string[] {
    const { agents, users } = this.server.state;
    const lower = text.toLowerCase();
    const ids = [...agents.values()].filter((a) => lower.includes(`@${a.name.toLowerCase()}`)).map((a) => a.agentId);
    for (const user of users.values()) if (lower.includes(`@${user.username.toLowerCase()}`)) ids.push(user.id);
    return ids;
  }

  /** v0.0.4 🍊 Posts a complete message as an agent (no streaming). */
  postAgentMessage(agentId: string, content: string, options: AgentMessageOptions = {}): ChatMessage | null {
    const agent = this.server.state.agents.get(agentId);
    if (!agent) return null;
    const message = this.buildMessage(agent, content, 'NONE', options);
    this.server.state.messages.push(message);
    this.server.publish('chat.message', message, agentId);
    return message;
  }

  /** v0.0.4 🍊 Posts a small SYSTEM line. */
  postSystem(content: string): void {
    const { state } = this.server;
    const message: ChatMessage = {
      id: recordId('msg'),
      roomId: state.roomId,
      seq: this.server.nextSeq(),
      authorKind: 'SYSTEM',
      authorId: 'system',
      authorName: 'Yuzu',
      kind: 'SYSTEM',
      content,
      mentions: [],
      mentionAll: false,
      closure: true,
      causalDepth: 0,
      streamState: 'NONE',
      time: nowText(),
    };
    state.messages.push(message);
    this.server.publish('chat.message', message);
  }

  /** v0.0.4 🍊 Types and streams a message word by word (typing → STREAMING deltas → DONE). */
  async streamMessage(agentId: string, content: string, options: AgentMessageOptions = {}): Promise<ChatMessage | null> {
    const agent = this.activeAgent(agentId);
    if (!agent || this.busy.has(agentId)) return null;
    this.busy.add(agentId);
    this.interrupted.delete(agentId);
    const { server } = this;
    try {
      server.publish('chat.typing', { agentId, typing: true }, agentId);
      this.setStatus(agentId, 'TALKING', 'CHAT', 'Writing a message to the group chat');
      await sleep(randInt(700, 1300));
      const message = this.buildMessage(agent, '', 'STREAMING', options);
      server.state.messages.push(message);
      server.publish('chat.message', message, agentId);
      for (const chunk of chunkWords(content)) {
        await sleep(randInt(40, 110));
        if (!this.activeAgent(agentId) || this.interrupted.has(agentId)) {
          message.streamState = 'STOPPED';
          server.publish('chat.message', message, agentId);
          return message;
        }
        message.content += chunk;
        server.publish('chat.delta', { messageId: message.id, delta: chunk, streamState: 'STREAMING' }, agentId);
      }
      message.streamState = 'DONE';
      message.mentions = this.mentionsIn(message.content);
      server.publish('chat.delta', { messageId: message.id, delta: '', streamState: 'DONE' }, agentId);
      server.publish('chat.message', message, agentId);
      this.addMemory(agentId, 'OUT', 'SELF', `I said in the group chat: ${content}`);
      this.setStatus(agentId, 'IDLE', 'MONITOR', 'Message sent — back to my task list');
      return message;
    } finally {
      server.publish('chat.typing', { agentId, typing: false }, agentId);
      this.busy.delete(agentId);
    }
  }

  /** v0.0.4 🍊 Stops a running stream and cancels the open span (interrupt button). */
  interrupt(agentId: string): void {
    this.interrupted.add(agentId);
    this.endActivity(agentId, 'CANCELLED', 'Interrupted by a human');
  }

  /** v0.0.4 🍊 Starts a module span inside the agent's current trace (opening a MAIN run when needed). */
  beginActivity(agentId: string, module: ModuleKind, text: string): void {
    this.endActivity(agentId, 'END');
    let run = this.runs.get(agentId);
    if (!run || run.steps >= 5) {
      if (run) this.event(agentId, run, 'MAIN', 'END', `Run finished after ${run.steps} steps`, run.rootSpanId, null);
      run = { traceId: recordId('trace', agentId), rootSpanId: `span-${hex(8)}`, steps: 0, open: null };
      this.runs.set(agentId, run);
      this.event(agentId, run, 'MAIN', 'START', `Main loop: took ${randInt(1, 4)} messages from my pool`, run.rootSpanId, null);
      this.server.state.meter.simulate(agentId, 'MAIN');
    }
    run.steps++;
    if (module === 'MAIN') {
      this.event(agentId, run, 'MAIN', 'STATE', text, run.rootSpanId, null);
      return;
    }
    if (module === 'TOOL') {
      const outer = `span-${hex(8)}`;
      this.event(agentId, run, 'TOOL_CALLING', 'START', `Decomposing into tool calls: ${text}`, outer, run.rootSpanId);
      const inner = `span-${hex(8)}`;
      this.event(agentId, run, 'TOOL', 'START', text, inner, outer);
      run.open = { spanId: inner, module, outerSpanId: outer };
      this.server.state.meter.simulate(agentId, 'TOOL_CALLING');
      return;
    }
    const spanId = `span-${hex(8)}`;
    this.event(agentId, run, module, 'START', text, spanId, run.rootSpanId);
    if (Math.random() < 0.5) this.event(agentId, run, module, 'STATE', 'Structured output validated (json_schema)', spanId, run.rootSpanId);
    run.open = { spanId, module };
    this.server.state.meter.simulate(agentId, module);
  }

  /** v0.0.4 🍊 Closes the agent's open span with END, ERROR or CANCELLED. */
  endActivity(agentId: string, phase: 'END' | 'ERROR' | 'CANCELLED', text?: string): void {
    const run = this.runs.get(agentId);
    const open = run?.open;
    if (!run || !open) return;
    const latency = randInt(300, 4200);
    const closing = text ?? `Done in ${(latency / 1000).toFixed(1)} s`;
    // v0.0.30 🍊 Every finished span also leaves a recorded model call, so the Trace tab's raw-request
    // view has real-looking JSON to inspect in the mock backend.
    const recorded = recordLlmCall(this.server.state.llmCalls, agentId, open.module, run.traceId, closing, latency,
      phase === 'ERROR');
    const detail = { model: TIER_MODELS[tierOf(open.module)], latencyMs: latency, llmCallId: recorded.call.id };
    this.event(agentId, run, open.module, phase, closing, open.spanId, open.outerSpanId ?? run.rootSpanId, detail);
    if (open.outerSpanId) {
      this.event(agentId, run, 'TOOL_CALLING', phase === 'ERROR' ? 'ERROR' : phase, 'Tool batch finished', open.outerSpanId, run.rootSpanId);
    }
    run.open = null;
  }

  /** v0.0.4 🍊 Publishes an asynchronous `error` event (toast + trace log in the UI). */
  publishError(agentId: string | null, code: ErrorCode, message: string, details: Record<string, unknown>): void {
    this.server.publish('error', { code, message, details, agentId, time: formatFullTime(new Date()) }, agentId);
  }

  /** v0.0.4 🍊 Appends to an agent's working memory, compacting at 20 entries (keep the latest 10). */
  addMemory(agentId: string, direction: 'IN' | 'OUT', origin: string, text: string): void {
    const view = this.server.state.workingMemory.get(agentId);
    if (!view) return;
    view.entries.push({ id: recordId('wm', agentId), direction, origin, text, time: nowText() });
    if (view.entries.length >= 20) {
      const removed = view.entries.splice(0, view.entries.length - 10);
      view.digest = `${view.digest ?? ''} Later: ${removed.length} older entries were compacted (latest: "${removed.at(-1)?.text.slice(0, 60) ?? ''}").`.trim();
    }
  }

  /** v0.0.4 🍊 Publishes an agent's task list view. */
  publishTaskList(view: TaskListView): void {
    this.server.state.taskLists.set(view.agentId, view);
    this.server.publish('task.list', view, view.agentId);
  }

  /** v0.0.4 🍊 Updates and publishes a ticket. */
  publishTicket(ticket: Ticket): void {
    ticket.updatedTime = nowText();
    this.server.state.tickets.set(ticket.id, ticket);
    this.server.publish('ticket.upsert', ticket, ticket.assigneeId ?? null);
  }

  private event(
    agentId: string,
    run: Run,
    module: ModuleKind,
    phase: 'START' | 'STATE' | 'END' | 'ERROR' | 'CANCELLED' | 'INFO',
    text: string,
    spanId: string,
    parentSpanId: string | null,
    detail: Record<string, unknown> | null = null,
  ): void {
    this.server.publish(
      'module.event',
      { id: recordId('event', agentId), agentId, module, phase, text, detail, traceId: run.traceId, spanId, parentSpanId, time: nowText() },
      agentId,
    );
  }

  private buildMessage(agent: Agent, content: string, streamState: ChatMessage['streamState'], options: AgentMessageOptions): ChatMessage {
    return {
      id: recordId('msg', agent.agentId),
      roomId: this.server.state.roomId,
      seq: this.server.nextSeq(),
      authorKind: 'AGENT',
      authorId: agent.agentId,
      authorName: agent.name,
      kind: options.kind ?? 'TEXT',
      content,
      mentions: this.mentionsIn(content),
      mentionAll: options.mentionAll ?? false,
      closure: false,
      causalDepth: 1,
      replyTo: options.replyTo ?? null,
      cardId: options.cardId ?? null,
      streamState,
      traceId: this.runs.get(agent.agentId)?.traceId ?? null,
      time: nowText(),
    };
  }
}
