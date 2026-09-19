import type { EventPhase, ModuleEvent, ModuleKind } from '../../../api/types';
import { parseNaturalTime } from '../../../lib/time';

/** v0.0.4 🍊 One span of a trace (all events sharing a spanId) with its children. */
export interface SpanNode {
  spanId: string;
  parentSpanId: string | null;
  module: ModuleKind;
  agentId: string;
  events: ModuleEvent[];
  /** Text of the START event (or the first event). */
  title: string;
  /** Phase of the last event (END, ERROR, CANCELLED, or still open). */
  finalPhase: EventPhase;
  start: number;
  end: number;
  depth: number;
  children: SpanNode[];
}

/** v0.0.4 🍊 A trace laid out for the waterfall. */
export interface SpanTree {
  /** Depth-first order, ready to render as indented rows. */
  rows: SpanNode[];
  t0: number;
  t1: number;
  eventCount: number;
}

/**
 * v0.0.4 🍊 Groups events by spanId and nests spans under parentSpanId. Events without a span become
 * single-event spans. Times come from natural-language strings (1 s resolution); missing times fall
 * back to arrival order so the bars still line up.
 */
export function buildSpanTree(events: readonly ModuleEvent[]): SpanTree {
  const spans = new Map<string, SpanNode>();
  const order: string[] = [];
  let fallbackClock = 0;
  let t0 = Number.POSITIVE_INFINITY;
  let t1 = Number.NEGATIVE_INFINITY;

  events.forEach((event, index) => {
    const parsed = parseNaturalTime(event.time);
    const t = Number.isNaN(parsed) ? (fallbackClock = Math.max(fallbackClock, index * 1000)) : parsed;
    t0 = Math.min(t0, t);
    t1 = Math.max(t1, t);
    const key = event.spanId ?? `event:${event.id}`;
    let span = spans.get(key);
    if (!span) {
      span = {
        spanId: key,
        parentSpanId: event.parentSpanId ?? null,
        module: event.module,
        agentId: event.agentId,
        events: [],
        title: event.text,
        finalPhase: event.phase,
        start: t,
        end: t,
        depth: 0,
        children: [],
      };
      spans.set(key, span);
      order.push(key);
    }
    span.events.push(event);
    span.parentSpanId ??= event.parentSpanId ?? null;
    if (event.phase === 'START') span.title = event.text;
    if (event.phase !== 'STATE' && event.phase !== 'INFO') span.finalPhase = event.phase;
    span.start = Math.min(span.start, t);
    span.end = Math.max(span.end, t);
  });

  const roots: SpanNode[] = [];
  for (const key of order) {
    const span = spans.get(key);
    if (!span) continue;
    const parent = span.parentSpanId ? spans.get(span.parentSpanId) : undefined;
    if (parent && parent !== span) parent.children.push(span);
    else roots.push(span);
  }

  const rows: SpanNode[] = [];
  const visit = (node: SpanNode, depth: number, seen: Set<string>) => {
    if (seen.has(node.spanId)) return;
    seen.add(node.spanId);
    node.depth = depth;
    rows.push(node);
    for (const child of node.children) visit(child, depth + 1, seen);
  };
  const seen = new Set<string>();
  for (const root of roots) visit(root, 0, seen);

  return {
    rows,
    t0: Number.isFinite(t0) ? t0 : 0,
    t1: Number.isFinite(t1) ? t1 : 0,
    eventCount: events.length,
  };
}

/** v0.0.4 🍊 Merges fetched and live events of one trace (dedupe by id, chronological). */
export function mergeTraceEvents(fetched: readonly ModuleEvent[], live: readonly ModuleEvent[]): ModuleEvent[] {
  const seen = new Set(fetched.map((e) => e.id));
  const merged = [...fetched];
  for (const event of live) if (!seen.has(event.id)) merged.push(event);
  return merged
    .map((event, index) => ({ event, index, t: parseNaturalTime(event.time) }))
    .sort((a, b) => (Number.isNaN(a.t) || Number.isNaN(b.t) ? 0 : a.t - b.t) || a.index - b.index)
    .map((entry) => entry.event);
}
