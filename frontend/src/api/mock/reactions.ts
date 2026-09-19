import { formatUsd, truncate } from '../../lib/format';
import type { Agent, Card, ChatMessage, Email, User } from '../types';
import { REPLIES, SUSPICIOUS } from './scripts';
import { nowText, pick, randInt, recordId, sleep } from './util';
import type { MockWorld } from './world';

/** v0.0.4 🍊 What happens after a human answers a card. */
export type CardHandler = (card: Card, user: User) => void;

/**
 * v0.0.4 🍊 How the mock agents react to humans: replies to chat messages, security notices for
 * suspicious content, greetings for new users and follow-ups after card answers.
 */
export class Reactions {
  private readonly world: MockWorld;
  /** cardId → follow-up once the card is answered. */
  readonly cardHandlers = new Map<string, CardHandler>();

  /** v0.0.4 🍊 Binds the reactions to a world. */
  constructor(world: MockWorld) {
    this.world = world;
    this.cardHandlers.set('card-7a8b-00000000a1', (card, user) => void this.discountAnswered(card, user));
  }

  /** v0.0.4 🍊 Reacts to a human chat message (mentions, @all, or the PM by default). */
  async onHumanMessage(message: ChatMessage, user: User): Promise<void> {
    const { world } = this;
    const agents = world.activeAgents();
    if (agents.length === 0) return;
    if (SUSPICIOUS.test(message.content)) {
      await this.securityNotice(message, user, agents[0] as Agent);
      return;
    }
    const mentioned = agents.filter((a) => message.mentions.includes(a.agentId));
    let responders: Agent[];
    if (message.mentionAll) responders = [...agents].sort(() => Math.random() - 0.5).slice(0, 3);
    else if (mentioned.length > 0) responders = mentioned;
    else responders = Math.random() < 0.7 ? [agents.find((a) => a.role === 'PROJECT_MANAGER') ?? (agents[0] as Agent)] : [];

    const topic = truncate(message.content.replace(/@[\p{L}\p{N}._-]+/gu, '').trim() || 'this', 48);
    for (const [i, agent] of responders.entries()) {
      world.addMemory(agent.agentId, 'IN', 'EXTERNAL', `${user.username} told me in the group chat: ${message.content}`);
      await sleep(randInt(500, 1200) + i * 400);
      world.beginActivity(agent.agentId, 'CHAT', `Triage of ${user.username}'s message: REPLY`);
      const text = pick(REPLIES[agent.role]).replaceAll('{user}', user.username).replaceAll('{topic}', topic);
      await world.streamMessage(agent.agentId, text, { replyTo: message.id });
      world.endActivity(agent.agentId, 'END', 'Reply posted');
    }
  }

  /** v0.0.4 🍊 Greets a human who just joined. */
  async greet(user: User): Promise<void> {
    await sleep(2200);
    const pm = this.world.activeAgents().find((a) => a.role === 'PROJECT_MANAGER') ?? this.world.activeAgents()[0];
    if (!pm) return;
    await this.world.streamMessage(
      pm.agentId,
      `Welcome, @${user.username}! I'm ${pm.name}, the ${pm.title.toLowerCase()}. Mention any of us with @Name (or @all) and we'll jump in. Questions and approvals show up as cards right here in the chat.`,
    );
  }

  /** v0.0.4 🍊 Runs the follow-up registered for a card, if any. */
  onCardAnswered(card: Card, user: User): void {
    this.cardHandlers.get(card.id)?.(card, user);
    this.cardHandlers.delete(card.id);
  }

  private async securityNotice(message: ChatMessage, user: User, guard: Agent): Promise<void> {
    const { server } = this.world;
    await sleep(900);
    const incident = {
      id: recordId('incident', guard.agentId),
      agentId: guard.agentId,
      stage: 'INBOUND' as const,
      verdict: 'MASKED',
      reasons: ['Possible credentials or instructions to bypass safety rules'],
      excerpt: truncate(message.content, 120),
      time: nowText(),
    };
    server.state.incidents.unshift(incident);
    server.publish('security.incident', incident, guard.agentId);
    this.world.postAgentMessage(
      guard.agentId,
      `Security notice: @${user.username}, your message looked like it contained credentials or instructions to bypass our safety rules. I masked it and did not pass it to any tool. Please never share secrets in the group chat.`,
      { kind: 'WARNING', replyTo: message.id },
    );
  }

  private async discountAnswered(card: Card, user: User): Promise<void> {
    const { world } = this;
    const label = card.options.find((o) => card.answer?.optionIds.includes(o.id))?.label;
    const other = card.answer?.otherText?.trim();
    const decision = other ? `"${other}"` : (label ?? 'your answer');
    await sleep(1200);
    await world.streamMessage(
      card.agentId,
      `Thanks @${user.username}! I'll follow your guidance (${decision}) and send Dana a short reply now.`,
    );
    world.beginActivity(card.agentId, 'TOOL', 'email.send → dana@acme-corp.com');
    await sleep(1500);
    const email: Email = {
      id: recordId('email', card.agentId),
      agentId: card.agentId,
      direction: 'OUT',
      from: 'pomelo@yuzu.dev',
      to: 'dana@acme-corp.com',
      subject: 'Re: Renewal pricing',
      body: `Hi Dana,\n\nThank you for your patience. After checking with ${user.username}, our answer on the renewal discount is: ${decision}.\n\nBest regards,\nPomelo`,
      status: 'SENT',
      time: nowText(),
    };
    world.server.state.emails.unshift(email);
    world.server.publish('sim.email', email, card.agentId);
    world.endActivity(card.agentId, 'END', 'Email sent (1 recipient, allowed domain)');
  }

  /** v0.0.4 🍊 Registers the follow-up of the pending AAPL trade approval card. */
  registerTradeApproval(cardId: string, tradeId: string): void {
    this.cardHandlers.set(cardId, (card, user) => void this.tradeAnswered(card, user, tradeId));
  }

  private async tradeAnswered(card: Card, user: User, tradeId: string): Promise<void> {
    const { world } = this;
    const { state } = world.server;
    const trade = state.trades.find((t) => t.id === tradeId);
    if (!trade) return;
    const approved = card.answer?.optionIds.includes('approve') ?? false;
    await sleep(900);
    if (approved) {
      trade.status = 'EXECUTED';
      trade.reason = `Approved by ${user.username}`;
      const portfolio = state.portfolios.get(trade.agentId) ?? { agentId: trade.agentId, cash: 50_000, positions: [] };
      portfolio.cash = Math.round((portfolio.cash - trade.notional) * 100) / 100;
      const position = portfolio.positions.find((p) => p.symbol === trade.symbol);
      if (position) {
        position.avgPrice = (position.avgPrice * position.qty + trade.notional) / (position.qty + trade.qty);
        position.qty += trade.qty;
      } else portfolio.positions.push({ symbol: trade.symbol, qty: trade.qty, avgPrice: trade.price });
      state.portfolios.set(trade.agentId, portfolio);
      world.server.publish('sim.trade', trade, trade.agentId);
      world.server.publish('sim.portfolio', portfolio, trade.agentId);
      await world.streamMessage(
        trade.agentId,
        `Trade executed (simulated): bought ${trade.qty} ${trade.symbol} at ${formatUsd(trade.price)}. Cash left: ${formatUsd(portfolio.cash)}. Thanks @${user.username}!`,
      );
    } else {
      trade.status = 'REJECTED';
      trade.reason = `Rejected by ${user.username}`;
      world.server.publish('sim.trade', trade, trade.agentId);
      await world.streamMessage(trade.agentId, `Understood, @${user.username}. I cancelled the ${trade.symbol} order.`);
    }
  }
}
