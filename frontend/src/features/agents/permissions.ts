import type { Limits, Permission, RoleKey } from '../../api/types';

/** v0.0.4 🍊 One permission with its UI label. */
export interface PermissionInfo {
  key: Permission;
  label: string;
  hint: string;
  /** High-risk permissions are highlighted (they can reach the outside world). */
  risky?: boolean;
}

/** v0.0.4 🍊 Permissions grouped by area for the checklists. */
export const PERMISSION_GROUPS: { label: string; items: PermissionInfo[] }[] = [
  {
    label: 'Chat',
    items: [
      { key: 'CHAT_POST', label: 'Post in chat', hint: 'Write messages in the group chat' },
      { key: 'CHAT_MENTION_ALL', label: 'Mention @all', hint: 'Notify everyone at once' },
      { key: 'ASK_USER', label: 'Ask humans', hint: 'Open question and approval cards' },
    ],
  },
  {
    label: 'Tasks',
    items: [
      { key: 'TASK_ASSIGN', label: 'Assign tickets', hint: 'Create and assign tickets to coworkers' },
      { key: 'TASK_APPROVE', label: 'Approve lists', hint: "Approve and archive other agents' task lists" },
    ],
  },
  {
    label: 'Knowledge',
    items: [
      { key: 'WEB_BROWSE', label: 'Browse the web', hint: 'Fetch and read web pages' },
      { key: 'FILE_READ', label: 'Read files', hint: 'Read files in its workspace' },
      { key: 'FILE_WRITE', label: 'Write files', hint: 'Create and edit files in its workspace' },
      { key: 'MEMORY_RECALL', label: 'Recall memory', hint: 'Search its deep memory' },
    ],
  },
  {
    label: 'Email (simulated)',
    items: [
      { key: 'EMAIL_READ', label: 'Read email', hint: 'Read the simulated inbox' },
      { key: 'EMAIL_SEND', label: 'Send email', hint: 'Send to allowed domains only', risky: true },
    ],
  },
  {
    label: 'Trading (simulated)',
    items: [
      { key: 'TRADE_VIEW', label: 'View portfolio', hint: 'See quotes and positions' },
      { key: 'TRADE_EXECUTE', label: 'Execute trades', hint: 'Within notional limits; above auto-approve needs a human', risky: true },
    ],
  },
  {
    label: 'Code',
    items: [
      { key: 'CODE_WRITE', label: 'Write code', hint: 'Create scripts in its sandbox' },
      { key: 'CODE_EXECUTE', label: 'Run code', hint: 'Execute code in its sandbox', risky: true },
    ],
  },
];

/** v0.0.4 🍊 Short labels of the role presets. */
export const ROLE_LABELS: Record<RoleKey, string> = {
  PROJECT_MANAGER: 'Project manager',
  RESEARCHER: 'Researcher',
  ENGINEER: 'Engineer',
  CUSTOMER_LIAISON: 'Customer liaison',
  FINANCE_ANALYST: 'Finance analyst',
};

/** v0.0.4 🍊 Editable (string) form of Limits so half-typed numbers survive re-renders. */
export interface LimitsDraft {
  emailAllowedDomains: string;
  emailMaxPerHour: string;
  tradeMaxNotionalUsd: string;
  tradeAutoApproveUsd: string;
  fileQuotaMb: string;
}

/** v0.0.4 🍊 Limits → form draft. */
export function limitsToDraft(limits: Limits): LimitsDraft {
  return {
    emailAllowedDomains: (limits.emailAllowedDomains ?? []).join(', '),
    emailMaxPerHour: String(limits.emailMaxPerHour ?? 0),
    tradeMaxNotionalUsd: String(limits.tradeMaxNotionalUsd ?? 0),
    tradeAutoApproveUsd: String(limits.tradeAutoApproveUsd ?? 0),
    fileQuotaMb: String(limits.fileQuotaMb ?? 0),
  };
}

/** v0.0.4 🍊 Form draft → Limits, with per-field validation errors. */
export function draftToLimits(draft: LimitsDraft): { limits: Limits; errors: Partial<Record<keyof LimitsDraft, string>> } {
  const errors: Partial<Record<keyof LimitsDraft, string>> = {};
  const num = (field: Exclude<keyof LimitsDraft, 'emailAllowedDomains'>) => {
    const value = Number(draft[field].replace(/[,\s$]/g, ''));
    if (!Number.isFinite(value) || value < 0) errors[field] = 'Enter a number ≥ 0';
    return Number.isFinite(value) ? Math.max(0, value) : 0;
  };
  const domains = draft.emailAllowedDomains
    .split(/[\s,;]+/)
    .map((d) => d.trim().toLowerCase().replace(/^@/, ''))
    .filter(Boolean);
  const invalid = domains.filter((d) => !/^[a-z0-9-]+(\.[a-z0-9-]+)+$/.test(d));
  if (invalid.length > 0) errors.emailAllowedDomains = `Not a domain: ${invalid.join(', ')}`;
  const limits: Limits = {
    emailAllowedDomains: [...new Set(domains)],
    emailMaxPerHour: Math.round(num('emailMaxPerHour')),
    tradeMaxNotionalUsd: num('tradeMaxNotionalUsd'),
    tradeAutoApproveUsd: num('tradeAutoApproveUsd'),
    fileQuotaMb: Math.round(num('fileQuotaMb')),
  };
  if (!errors.tradeAutoApproveUsd && limits.tradeAutoApproveUsd > limits.tradeMaxNotionalUsd && limits.tradeMaxNotionalUsd > 0) {
    errors.tradeAutoApproveUsd = 'Cannot exceed the max notional';
  }
  return { limits, errors };
}
