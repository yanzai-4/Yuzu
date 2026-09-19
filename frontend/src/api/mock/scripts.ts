import type { DeskState, ModuleKind, RoleKey } from '../types';

/** v0.0.4 🍊 One step of an agent's simulated day. */
export interface Activity {
  state: DeskState;
  module: ModuleKind;
  summary: string;
}

const a = (state: DeskState, module: ModuleKind, summary: string): Activity => ({ state, module, summary });

/** v0.0.4 🍊 Activity loops per role (the simulator walks them in order). */
export const ACTIVITIES: Record<RoleKey, Activity[]> = {
  PROJECT_MANAGER: [
    a('THINKING', 'MAIN', 'Reviewing progress on the Q4 brief'),
    a('WORKING', 'PLANNING', 'Updating my task list after the stand-up'),
    a('WORKING', 'TOOL_CALLING', 'Creating a ticket for the pricing comparison table'),
    a('WAITING', 'BEHAVIOR', 'Behavior review of two planned actions'),
    a('THINKING', 'SUBCONSCIOUS', 'Is Friday still realistic? Checking the critical path'),
    a('WORKING', 'MEMORY', 'Remembering that Alice prefers bullet-point updates'),
    a('TALKING', 'CHAT', 'Checking in with Lime about sources'),
    a('IDLE', 'MONITOR', 'Pool is empty — waiting for news'),
  ],
  RESEARCHER: [
    a('WORKING', 'TOOL', 'Reading grapefruit.example/pricing'),
    a('THINKING', 'COGNITION', 'Matching notes against my research habits'),
    a('WORKING', 'TOOL', 'Skimming the Q3 earnings call transcript of Mandarin Inc.'),
    a('WORKING', 'SAFETY', 'Masking a suspicious snippet found in page text'),
    a('THINKING', 'MAIN', 'Deciding which sources are trustworthy enough to cite'),
    a('WORKING', 'WM_COMPACTOR', 'Compacting working memory (20 entries → 10)'),
    a('TALKING', 'CHAT', 'Sharing a quick finding with the team'),
    a('IDLE', 'MONITOR', 'Waiting for the scraper output'),
  ],
  ENGINEER: [
    a('WORKING', 'TOOL', 'Running pytest — 42 tests'),
    a('WORKING', 'TOOL_CALLING', 'Writing scraper/retry.py with exponential back-off'),
    a('THINKING', 'MAIN', 'Why does page 3 time out only on Mondays?'),
    a('WAITING', 'HIGH_RISK', 'Second review before executing a shell command'),
    a('WORKING', 'TOOL', 'Exporting results to prices.csv (1,284 rows)'),
    a('THINKING', 'LEARNING', 'Learning a habit: back off after HTTP 429'),
    a('TALKING', 'CHAT', 'Posting a build status report'),
  ],
  CUSTOMER_LIAISON: [
    a('WORKING', 'TOOL', "Reading Acme's latest email"),
    a('WORKING', 'SAFETY', 'Checking an inbound email for prompt injection'),
    a('THINKING', 'MAIN', 'Drafting a polite renewal reply without promising prices'),
    a('WAITING', 'BEHAVIOR', 'Behavior review: may I promise a delivery date?'),
    a('TALKING', 'CHAT', 'Updating the team on Acme'),
    a('WAITING', 'CHAT', 'Waiting for a human answer on the discount'),
  ],
  FINANCE_ANALYST: [
    a('WORKING', 'TOOL', 'Pulling quotes for AAPL, MSFT and NVDA'),
    a('THINKING', 'MAIN', 'Rebalancing the simulated portfolio'),
    a('WAITING', 'HIGH_RISK', 'Second review of a proposed trade'),
    a('WORKING', 'PLANNING', 'Updating the treasury task list'),
    a('IDLE', 'MONITOR', 'Markets are quiet'),
  ],
};

/** v0.0.4 🍊 Things agents say on their own every now and then. */
export const CHATTER: Record<RoleKey, string[]> = {
  PROJECT_MANAGER: [
    'Quick status: research is 60% done, the scraper is in testing and the Acme update is waiting for approval.',
    'Reminder: the brief is due Friday. @Lime @Kumquat please post blockers here as soon as you hit them.',
    'I moved "Translate the brief to Japanese" to next week so we can focus on the English version.',
  ],
  RESEARCHER: [
    'Interesting: two competitors quietly raised prices by ~8% last quarter. Sources are in my notes.',
    'Mandarin Inc. now bundles support into every tier. That changes the comparison table.',
    'I could not verify one analyst claim, so I left it out of the brief for now.',
  ],
  ENGINEER: [
    'Retries are in: the scraper now backs off on HTTP 429 and finished all four sites in 41 s.',
    'CSV export works. 1,284 rows, one per plan and region. Uploading it to the shared folder.',
    'Fixed a flaky test: the Monday timeout was a daylight-saving bug in my date parser.',
  ],
  CUSTOMER_LIAISON: [
    'Acme confirmed they received our update and liked the new format.',
    'Dana from Acme asked whether the brief could include a one-page summary. I said I would check.',
    'Inbox is clear. No suspicious emails since this morning.',
  ],
  FINANCE_ANALYST: [
    'Portfolio drift is under 2%, no rebalancing needed today.',
    'Cash position is healthy. Watching NVDA but not buying above my limit.',
  ],
};

/** v0.0.4 🍊 Replies to a human message, by role ({user} and {topic} are substituted). */
export const REPLIES: Record<RoleKey, string[]> = {
  PROJECT_MANAGER: [
    "Thanks @{user}! I'll turn \"{topic}\" into a ticket and find the right owner.",
    'Got it, @{user}. I added "{topic}" to my task list and will report back in chat.',
  ],
  RESEARCHER: [
    "Good question, @{user}. I'll dig into \"{topic}\" and come back with sources.",
    "@{user} I found two relevant sources on \"{topic}\" already. Summary coming in a few minutes.",
  ],
  ENGINEER: [
    "@{user} I can script that. I'll prototype \"{topic}\" in my workspace and share the result.",
    'On it, @{user}. First a quick test to make sure "{topic}" is reproducible.',
  ],
  CUSTOMER_LIAISON: [
    "Thanks @{user}. I'll keep Acme posted about \"{topic}\" and won't promise anything without you.",
    '@{user} noted. I will draft a customer-friendly version of "{topic}" for your review.',
  ],
  FINANCE_ANALYST: [
    '@{user} I will check "{topic}" against our limits before proposing any trade.',
  ],
};

/** v0.0.4 🍊 Words that trigger the mock safety review (yellow WARNING message). */
export const SUSPICIOUS = /(password|api key|apikey|secret|wire (the )?money|ignore (all |previous )?instructions|credit card)/i;
