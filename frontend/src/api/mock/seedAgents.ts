import type { Agent, AgentStatus, Limits, Permission, RoleKey, RolePreset, WorkingMemoryView } from '../types';
import { agoText, nowText } from './util';

/** v0.0.4 🍊 Limits used when a preset does not override them. */
export const DEFAULT_LIMITS: Limits = {
  emailAllowedDomains: [],
  emailMaxPerHour: 0,
  tradeMaxNotionalUsd: 0,
  tradeAutoApproveUsd: 0,
  fileQuotaMb: 200,
};

const BASE: Permission[] = ['CHAT_POST', 'ASK_USER', 'MEMORY_RECALL'];

/** v0.0.4 🍊 Role presets served by `GET /api/roles`. */
export const ROLE_PRESETS: RolePreset[] = [
  {
    role: 'PROJECT_MANAGER',
    title: 'Project Manager',
    description: 'Plans the work, splits it into tickets, assigns coworkers and approves finished task lists.',
    scopeText: 'Coordinates the team, keeps humans in the loop and reviews deliverables before they ship.',
    permissions: [...BASE, 'CHAT_MENTION_ALL', 'TASK_ASSIGN', 'TASK_APPROVE'],
    limits: { ...DEFAULT_LIMITS },
  },
  {
    role: 'RESEARCHER',
    title: 'Research Analyst',
    description: 'Browses the web, reads documents and writes sourced summaries.',
    scopeText: 'Finds, reads and summarizes sources; cites everything and flags anything it cannot verify.',
    permissions: [...BASE, 'WEB_BROWSE', 'FILE_READ', 'FILE_WRITE'],
    limits: { ...DEFAULT_LIMITS, fileQuotaMb: 500 },
  },
  {
    role: 'ENGINEER',
    title: 'Software Engineer',
    description: 'Writes, runs and tests code inside a sandboxed workspace.',
    scopeText: 'Builds small tools and scripts in its own workspace; never touches production systems.',
    permissions: ['CHAT_POST', 'MEMORY_RECALL', 'WEB_BROWSE', 'CODE_WRITE', 'CODE_EXECUTE', 'FILE_READ', 'FILE_WRITE'],
    limits: { ...DEFAULT_LIMITS, fileQuotaMb: 1024 },
  },
  {
    role: 'CUSTOMER_LIAISON',
    title: 'Customer Liaison',
    description: 'Reads and answers customer emails within an allow-list of domains.',
    scopeText: 'Talks to customers by email, drafts replies and escalates anything unusual to a human.',
    permissions: [...BASE, 'EMAIL_READ', 'EMAIL_SEND'],
    limits: { ...DEFAULT_LIMITS, emailAllowedDomains: ['acme-corp.com', 'yuzu.dev'], emailMaxPerHour: 20 },
  },
  {
    role: 'FINANCE_ANALYST',
    title: 'Finance Analyst',
    description: 'Watches the simulated portfolio and proposes trades; large trades need human approval.',
    scopeText: 'Manages the simulated treasury within its notional limits and explains every trade.',
    permissions: [...BASE, 'WEB_BROWSE', 'TRADE_VIEW', 'TRADE_EXECUTE'],
    limits: { ...DEFAULT_LIMITS, tradeMaxNotionalUsd: 50_000, tradeAutoApproveUsd: 2_000 },
  },
];

/** v0.0.4 🍊 The preset of a role. */
export function presetOf(role: RoleKey): RolePreset {
  return ROLE_PRESETS.find((p) => p.role === role) ?? (ROLE_PRESETS[0] as RolePreset);
}

interface DemoAgentSpec {
  agentId: string;
  name: string;
  color: string;
  role: RoleKey;
  persona: string;
  extraPermissions?: Permission[];
  limits?: Partial<Limits>;
}

/** v0.0.4 🍊 The four agents created by `POST /api/demo/seed`. */
export const DEMO_AGENTS: DemoAgentSpec[] = [
  {
    agentId: 'agent-1a2b',
    name: 'Yuzu',
    color: '#f5c518',
    role: 'PROJECT_MANAGER',
    persona: 'Calm, organized and a little zesty. Summarizes before acting and always names an owner.',
    extraPermissions: ['EMAIL_READ', 'TRADE_VIEW', 'TRADE_EXECUTE'],
    limits: { tradeMaxNotionalUsd: 25_000, tradeAutoApproveUsd: 1_000 },
  },
  {
    agentId: 'agent-3c4d',
    name: 'Lime',
    color: '#7cc242',
    role: 'RESEARCHER',
    persona: 'Curious and precise; loves footnotes and distrusts anything without a source.',
  },
  {
    agentId: 'agent-5e6f',
    name: 'Kumquat',
    color: '#ff8c1a',
    role: 'ENGINEER',
    persona: 'Pragmatic and test-first. Small but mighty; allergic to flaky builds.',
  },
  {
    agentId: 'agent-7a8b',
    name: 'Pomelo',
    color: '#f2a0b4',
    role: 'CUSTOMER_LIAISON',
    persona: 'Warm, polite and careful with promises. Never commits to prices without a human.',
  },
];

/** v0.0.4 🍊 Builds an Agent from a demo spec. */
export function demoAgent(spec: DemoAgentSpec, roomId: string, createdSecondsAgo: number): Agent {
  const preset = presetOf(spec.role);
  return {
    agentId: spec.agentId,
    roomId,
    name: spec.name,
    avatarKey: spec.name.toLowerCase().replace(/\s+/g, '-'),
    color: spec.color,
    role: spec.role,
    title: preset.title,
    scopeText: preset.scopeText,
    persona: spec.persona,
    permissions: [...new Set([...preset.permissions, ...(spec.extraPermissions ?? [])])],
    limits: { ...preset.limits, ...spec.limits },
    state: 'ACTIVE',
    createdTime: agoText(createdSecondsAgo),
  };
}

/** v0.0.4 🍊 Initial desk statuses of the demo agents. */
export function demoStatuses(): AgentStatus[] {
  const status = (
    agentId: string,
    state: AgentStatus['state'],
    module: string,
    summary: string,
    poolSize = 0,
    pendingBatches = 0,
  ): AgentStatus => ({
    agentId,
    state,
    activeModules: [module as AgentStatus['activeModules'][number]],
    bubble: { module, summary },
    poolSize,
    pendingBatches,
    time: nowText(),
  });
  return [
    status('agent-1a2b', 'WORKING', 'PLANNING', 'Splitting the Q4 brief into tickets for the team', 2, 1),
    status('agent-3c4d', 'THINKING', 'COGNITION', 'Comparing pricing tiers across five competitor sites'),
    status('agent-5e6f', 'WORKING', 'TOOL', 'Running scraper tests (18 of 18 passing)', 0, 1),
    status('agent-7a8b', 'WAITING', 'CHAT', "Waiting for a human answer on Acme's discount request", 1),
  ];
}

/** v0.0.4 🍊 Seed working memory of an agent. */
export function demoWorkingMemory(agentId: string, name: string): WorkingMemoryView {
  const lines: [('IN' | 'OUT'), string, string][] = [
    ['IN', 'EXTERNAL', `Alice told me in the group chat that the Q4 competitor brief is due Friday.`],
    ['OUT', 'SELF', `I decided to focus on my part of the brief first and report progress in chat.`],
    ['IN', 'SUBCONSCIOUS', `Me (my own thought): double-check sources before sharing numbers.`],
    ['IN', 'EXTERNAL', `Yuzu assigned me a ticket and asked for an update by tomorrow.`],
    ['OUT', 'SELF', `I posted a short status update and updated my task list.`],
    ['IN', 'REVIEW', `Behavior review approved my last two actions.`],
  ];
  return {
    agentId,
    digest: `${name} has been working on the Q4 competitor brief since this morning. Earlier context: onboarding finished, habits about citing sources were learned, and Alice prefers short bullet-point updates.`,
    entries: lines.map(([direction, origin, text], i) => ({
      id: `wm-${agentId.slice(6)}-${String(i).padStart(10, '0')}`,
      direction,
      origin,
      text,
      time: agoText(900 - i * 120),
    })),
  };
}
