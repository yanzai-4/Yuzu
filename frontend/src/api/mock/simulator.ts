import type { Agent, TaskList } from '../types';
import { ACTIVITIES, CHATTER } from './scripts';
import { runScenario, type ScenarioName } from './scenarios';
import { nowText, pick, randInt, recordId } from './util';
import type { Reactions } from './reactions';
import type { MockWorld } from './world';

const GOALS: Record<Agent['role'], string[]> = {
  PROJECT_MANAGER: ["Prepare Monday's planning meeting", 'Review the scraper output with Kumquat', 'Plan the Japanese translation'],
  RESEARCHER: ['Summarize analyst notes on citrus SaaS', 'Fact-check the competitor table'],
  ENGINEER: ['Automate the weekly usage report', 'Harden the scraper against layout changes'],
  CUSTOMER_LIAISON: ["Answer Acme's follow-up questions", 'Collect customer feedback on the pilot'],
  FINANCE_ANALYST: ['Rebalance the simulated portfolio', 'Write the monthly treasury note'],
};

/**
 * v0.0.4 🍊 Drives the mock office: desk states and traces every ~1.7 s, usage ticks every 2 s, task
 * progress, spontaneous chatter and a scripted timeline (email, pending trade, async error, ...).
 */
export class Simulator {
  private readonly world: MockWorld;
  private readonly reactions: Reactions;
  private readonly step = new Map<string, number>();
  private readonly errorUntil = new Map<string, number>();
  private running = false;

  /** v0.0.4 🍊 Binds the simulator to the world. */
  constructor(world: MockWorld, reactions: Reactions) {
    this.world = world;
    this.reactions = reactions;
  }

  /** v0.0.4 🍊 Starts every timer (idempotent). */
  start(): void {
    if (this.running) return;
    this.running = true;
    this.every(1700, () => this.tickAgents());
    this.every(2000, () => this.world.server.publish('usage.tick', this.world.server.state.meter.snapshot()));
    this.every(8000, () => this.progressTasks());
    this.every(24_000, () => void this.chatter());
    const timeline: [number, ScenarioName][] = [
      [3500, 'finding'],
      [15_000, 'email'],
      [28_000, 'trade'],
      [42_000, 'error'],
      [58_000, 'blockedEmail'],
      [75_000, 'report'],
    ];
    for (const [delay, name] of timeline) this.after(delay, () => this.scenario(name));
    this.every(90_000, () => this.scenario('error'));
  }

  /** v0.0.4 🍊 Marks an agent as erroring for a few seconds (the ticker leaves it alone). */
  holdError(agentId: string, ms: number): void {
    this.errorUntil.set(agentId, Date.now() + ms);
  }

  private scenario(name: ScenarioName): void {
    void runScenario(name, this.world, this.reactions, this);
  }

  private tickAgents(): void {
    for (const agent of this.world.activeAgents()) {
      if (this.world.busy.has(agent.agentId) || Math.random() < 0.45) continue;
      if ((this.errorUntil.get(agent.agentId) ?? 0) > Date.now()) continue;
      const script = ACTIVITIES[agent.role];
      const index = (this.step.get(agent.agentId) ?? randInt(0, script.length - 1)) % script.length;
      this.step.set(agent.agentId, index + 1);
      const activity = script[index];
      if (!activity) continue;
      this.world.beginActivity(agent.agentId, activity.module, activity.summary);
      this.world.setStatus(
        agent.agentId,
        activity.state,
        activity.module,
        activity.summary,
        activity.state === 'IDLE' ? 0 : randInt(0, 3),
        activity.state === 'WAITING' ? 1 : randInt(0, 1),
      );
    }
  }

  private progressTasks(): void {
    const { world } = this;
    const agents = world.activeAgents();
    if (agents.length === 0) return;
    const agent = pick(agents);
    const view = world.server.state.taskLists.get(agent.agentId) ?? { agentId: agent.agentId, current: null, recentArchived: [] };
    const list = view.current;
    if (!list) {
      this.assignNewList(agent);
      return;
    }
    if (list.status !== 'ACTIVE') return;
    const doing = list.items.find((i) => i.state === 'DOING');
    if (doing) doing.state = 'DONE';
    const todo = list.items.find((i) => i.state === 'TODO');
    if (todo && Math.random() < 0.12 && list.items.filter((i) => i.state === 'TODO').length > 1) {
      todo.state = 'STRUCK';
      todo.struckReason = 'No longer needed after the latest update from the team.';
    } else if (todo) {
      todo.state = 'DOING';
    }
    if (!list.items.some((i) => i.state === 'TODO' || i.state === 'DOING')) {
      list.status = 'AWAITING_APPROVAL';
      list.outcome = `Finished "${list.goal}". Summary posted in the group chat.`;
      const ticket = list.ticketId ? world.server.state.tickets.get(list.ticketId) : undefined;
      if (ticket) world.publishTicket({ ...ticket, status: 'DONE' });
      void world.streamMessage(agent.agentId, `My task list "${list.goal}" is done and waiting for approval in the Tasks tab.`);
    }
    world.publishTaskList(view);
  }

  private assignNewList(agent: Agent): void {
    const { world } = this;
    const goal = pick(GOALS[agent.role]);
    const ticketId = recordId('ticket');
    const listId = recordId('list', agent.agentId);
    const items = ['Clarify the goal with the requester', 'Collect the inputs', `Do the work: ${goal.toLowerCase()}`, 'Report back in chat'];
    const list: TaskList = {
      id: listId,
      agentId: agent.agentId,
      goal,
      status: 'ACTIVE',
      publisherId: 'agent-1a2b',
      publisherName: 'Yuzu',
      ticketId,
      items: items.map((text, i) => ({ id: recordId('item', agent.agentId), ord: i + 1, text, state: i === 0 ? 'DOING' : 'TODO' })),
      time: nowText(),
    };
    const previous = world.server.state.taskLists.get(agent.agentId);
    world.publishTaskList({ agentId: agent.agentId, current: list, recentArchived: previous?.recentArchived ?? [] });
    world.publishTicket({
      id: ticketId,
      roomId: world.server.state.roomId,
      title: goal,
      detail: `Picked up by ${agent.name}.`,
      status: 'IN_PROGRESS',
      creatorName: 'Yuzu',
      assigneeId: agent.agentId,
      requesterName: 'Alice',
      listId,
      time: nowText(),
      updatedTime: nowText(),
    });
  }

  private async chatter(): Promise<void> {
    const agents = this.world.activeAgents().filter((a) => !this.world.busy.has(a.agentId));
    if (agents.length === 0 || Math.random() < 0.35) return;
    const agent = pick(agents);
    await this.world.streamMessage(agent.agentId, pick(CHATTER[agent.role]));
  }

  private every(ms: number, fn: () => void): void {
    const loop = () => {
      if (!this.running) return;
      fn();
      setTimeout(loop, ms * (0.8 + Math.random() * 0.4));
    };
    setTimeout(loop, ms);
  }

  private after(ms: number, fn: () => void): void {
    setTimeout(() => this.running && fn(), ms);
  }
}
