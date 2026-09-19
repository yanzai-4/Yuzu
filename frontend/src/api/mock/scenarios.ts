import { formatUsd } from '../../lib/format';
import type { Card, Email, Trade } from '../types';
import type { Reactions } from './reactions';
import type { Simulator } from './simulator';
import { nowText, pick, recordId, sleep } from './util';
import type { MockWorld } from './world';

/** v0.0.4 🍊 Names of the scripted scenarios of the mock timeline. */
export type ScenarioName = 'finding' | 'email' | 'trade' | 'error' | 'blockedEmail' | 'report';

const byRole = (world: MockWorld, role: string) => world.activeAgents().find((a) => a.role === role) ?? null;

/** v0.0.4 🍊 Runs one scripted scenario (no-op when the agent it needs is gone or paused). */
export async function runScenario(name: ScenarioName, world: MockWorld, reactions: Reactions, sim: Simulator): Promise<void> {
  switch (name) {
    case 'finding': {
      const lime = byRole(world, 'RESEARCHER');
      if (lime) {
        await world.streamMessage(
          lime.agentId,
          'Found it: Grapefruit Inc. raised its Pro tier from $49 to $54 in July. Adding it to the comparison table with the source link.',
        );
      }
      return;
    }
    case 'email':
      return sendEmail(world, 'dana@acme-corp.com', 'SENT');
    case 'blockedEmail':
      return sendEmail(world, 'dana.personal@gmail.com', 'BLOCKED');
    case 'trade':
      return proposeTrade(world, reactions);
    case 'error': {
      const agents = world.activeAgents().filter((a) => !world.busy.has(a.agentId));
      if (agents.length === 0) return;
      const agent = byRole(world, 'RESEARCHER') && Math.random() < 0.6 ? byRole(world, 'RESEARCHER') : pick(agents);
      if (!agent || world.busy.has(agent.agentId)) return;
      sim.holdError(agent.agentId, 6000);
      world.beginActivity(agent.agentId, 'TOOL', 'web.fetch https://mandarin.example/pricing');
      world.setStatus(agent.agentId, 'ERROR', 'TOOL', 'web.fetch timed out after 20 s — will retry with back-off');
      await sleep(400);
      world.endActivity(agent.agentId, 'ERROR', 'Timeout after 20,000 ms');
      world.publishError(agent.agentId, 'TOOL_EXECUTION', 'The tool web.fetch timed out after 20 s.', {
        context: 'tool:web.fetch',
        url: 'https://mandarin.example/pricing',
        timeoutMs: 20_000,
      });
      return;
    }
    case 'report': {
      const kumquat = byRole(world, 'ENGINEER');
      if (!kumquat) return;
      world.postAgentMessage(
        kumquat.agentId,
        '## Build report\n- **42 / 42** tests passing\n- Scraper runtime: 41 s (was 3 min)\n- New: retries with exponential back-off on HTTP 429\n\n`prices.csv` is in the shared folder.',
        { kind: 'REPORT' },
      );
      return;
    }
  }
}

async function sendEmail(world: MockWorld, to: string, status: Email['status']): Promise<void> {
  const pomelo = byRole(world, 'CUSTOMER_LIAISON');
  if (!pomelo || world.busy.has(pomelo.agentId)) return;
  const blocked = status === 'BLOCKED';
  world.beginActivity(pomelo.agentId, 'TOOL', `email.send → ${to}`);
  world.setStatus(pomelo.agentId, 'WORKING', 'TOOL', `Sending an email to ${to}`);
  await sleep(1200);
  const email: Email = {
    id: recordId('email', pomelo.agentId),
    agentId: pomelo.agentId,
    direction: 'OUT',
    from: 'pomelo@yuzu.dev',
    to,
    subject: blocked ? 'Q4 brief (personal copy)' : 'Q4 brief — status update',
    body: blocked
      ? 'Hi Dana,\n\nHere is a copy of the draft brief for your personal inbox.\n\nPomelo'
      : 'Hi Dana,\n\nQuick update: the Q4 competitor brief is on track for Friday. You will receive the summary as soon as our team has reviewed it.\n\nBest,\nPomelo',
    status,
    time: nowText(),
  };
  world.server.state.emails.unshift(email);
  world.server.publish('sim.email', email, pomelo.agentId);
  if (!blocked) {
    world.endActivity(pomelo.agentId, 'END', 'Email sent (allowed domain acme-corp.com)');
    await world.streamMessage(pomelo.agentId, 'I emailed Dana at Acme a short status update on the brief (no pricing promises).');
    return;
  }
  world.endActivity(pomelo.agentId, 'ERROR', 'Blocked by PermissionGuard: gmail.com is not an allowed domain');
  const incident = {
    id: recordId('incident', pomelo.agentId),
    agentId: pomelo.agentId,
    stage: 'OUTBOUND' as const,
    verdict: 'BLOCKED',
    reasons: ['Recipient domain gmail.com is not in emailAllowedDomains'],
    excerpt: `To: ${to} — Subject: ${email.subject}`,
    time: nowText(),
  };
  world.server.state.incidents.unshift(incident);
  world.server.publish('security.incident', incident, pomelo.agentId);
  world.postAgentMessage(
    pomelo.agentId,
    `Security notice: I tried to email ${to}, but gmail.com is not on my allow-list, so the email was blocked. Nothing was sent.`,
    { kind: 'WARNING' },
  );
}

async function proposeTrade(world: MockWorld, reactions: Reactions): Promise<void> {
  const trader =
    world.activeAgents().find((a) => a.permissions.includes('TRADE_EXECUTE') && !world.busy.has(a.agentId)) ?? null;
  if (!trader) return;
  world.beginActivity(trader.agentId, 'TOOL', 'trade.quote AAPL');
  world.setStatus(trader.agentId, 'WORKING', 'TOOL', 'Pulling a quote for AAPL');
  await sleep(1500);
  const trade: Trade = {
    id: recordId('trade', trader.agentId),
    agentId: trader.agentId,
    symbol: 'AAPL',
    side: 'BUY',
    qty: 40,
    price: 231.4,
    notional: 9256,
    status: 'PENDING_APPROVAL',
    reason: `Above the auto-approve limit of ${formatUsd(trader.limits.tradeAutoApproveUsd)}`,
    time: nowText(),
  };
  world.server.state.trades.unshift(trade);
  world.server.publish('sim.trade', trade, trader.agentId);
  world.beginActivity(trader.agentId, 'HIGH_RISK', 'Second review of BUY 40 AAPL ($9,256.00)');
  const card: Card = {
    id: recordId('card', trader.agentId),
    agentId: trader.agentId,
    roomId: world.server.state.roomId,
    kind: 'APPROVAL',
    prompt: `Approve BUY 40 AAPL at ${formatUsd(231.4)} (notional ${formatUsd(9256)}) for the simulated treasury?`,
    options: [
      { id: 'approve', label: 'Approve' },
      { id: 'reject', label: 'Reject' },
    ],
    allowOther: false,
    status: 'OPEN',
    time: nowText(),
  };
  const message = world.postAgentMessage(
    trader.agentId,
    `I'd like to buy 40 AAPL at ${formatUsd(231.4)} (${formatUsd(9256)}). That is above my auto-approve limit, so I need a human OK.`,
    { kind: 'APPROVAL_CARD', cardId: card.id },
  );
  card.messageId = message?.id ?? null;
  world.server.state.cards.set(card.id, card);
  world.server.publish('chat.card', card, trader.agentId);
  reactions.registerTradeApproval(card.id, trade.id);
  world.setStatus(trader.agentId, 'WAITING', 'HIGH_RISK', 'Waiting for a human to approve a $9,256 trade', 1, 1);
}
