/**
 * v0.0.4 🍊 @-mention helpers: detect the query being typed, rank candidates and split message text
 * into plain and highlighted segments. Mentions are parsed server-side; this is display only.
 */

/** v0.0.4 🍊 Something that can be mentioned: an agent, a human or everyone. */
export interface MentionTarget {
  kind: 'agent' | 'human' | 'all';
  id: string;
  name: string;
  color?: string;
  avatarKey?: string;
  subtitle?: string;
}

/** v0.0.4 🍊 A run of message text, optionally recognized as a mention. */
export interface MentionSegment {
  text: string;
  target?: MentionTarget;
}

/** v0.0.4 🍊 The pseudo-target for `@all`. */
export const MENTION_ALL: MentionTarget = { kind: 'all', id: 'all', name: 'all', subtitle: 'Notify everyone' };

const QUERY = /(^|[\s(])@([\p{L}\p{N}._-]*(?: [\p{L}\p{N}._-]*)?)$/u;

/** v0.0.4 🍊 Returns the "@query" under the caret (start = index of '@'), or null. */
export function findMentionQuery(text: string, caret: number): { start: number; query: string } | null {
  const before = text.slice(0, caret);
  const m = QUERY.exec(before);
  if (!m) return null;
  const query = m[2] ?? '';
  return { start: caret - query.length - 1, query };
}

/** v0.0.4 🍊 Candidates whose name (or any word of it) starts with the query, best matches first. */
export function rankMentionTargets(query: string, targets: MentionTarget[], limit = 8): MentionTarget[] {
  const q = query.toLowerCase();
  const scored: { target: MentionTarget; score: number }[] = [];
  for (const target of targets) {
    const name = target.name.toLowerCase();
    let score = -1;
    if (q === '') score = target.kind === 'all' ? 1 : 2;
    else if (name === q) score = 0;
    else if (name.startsWith(q)) score = 1;
    else if (name.split(/[\s._-]+/).some((word) => word.startsWith(q))) score = 3;
    if (score >= 0) scored.push({ target, score });
  }
  scored.sort((a, b) => a.score - b.score || kindOrder(a.target) - kindOrder(b.target) || a.target.name.localeCompare(b.target.name));
  return scored.slice(0, limit).map((s) => s.target);
}

/** v0.0.4 🍊 Builds the matcher used by splitMentions (build once per roster). */
export function buildMentionMatcher(targets: MentionTarget[]): RegExp | null {
  const names = [...new Set(targets.map((t) => t.name))].filter(Boolean).sort((a, b) => b.length - a.length);
  if (names.length === 0) return null;
  return new RegExp(`@(${names.map(escapeRegExp).join('|')})(?![\\p{L}\\p{N}_-])`, 'giu');
}

/** v0.0.4 🍊 Splits text into plain and mention segments (case-insensitive, longest name wins). */
export function splitMentions(text: string, matcher: RegExp | null, targets: MentionTarget[]): MentionSegment[] {
  if (!matcher || !text.includes('@')) return [{ text }];
  const byName = new Map(targets.map((t) => [t.name.toLowerCase(), t]));
  const segments: MentionSegment[] = [];
  let last = 0;
  matcher.lastIndex = 0;
  for (let m = matcher.exec(text); m; m = matcher.exec(text)) {
    const target = byName.get((m[1] ?? '').toLowerCase());
    if (!target) continue;
    if (m.index > last) segments.push({ text: text.slice(last, m.index) });
    segments.push({ text: m[0], target });
    last = m.index + m[0].length;
  }
  if (last < text.length) segments.push({ text: text.slice(last) });
  return segments;
}

function kindOrder(t: MentionTarget): number {
  return t.kind === 'all' ? 0 : t.kind === 'agent' ? 1 : 2;
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
