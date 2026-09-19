import { useState } from 'react';
import { getLlmCallPayload } from '../../../api/client';
import type { LlmCall, ModuleEvent } from '../../../api/types';
import { AgentAvatar } from '../../../components/Avatars';
import { ModuleChip, PhaseChip } from '../../../components/Badge';
import { Button } from '../../../components/Button';
import { Spinner } from '../../../components/Spinner';
import { formatInt } from '../../../lib/format';
import { clockTimeWithSeconds } from '../../../lib/time';
import { useAsync } from '../../../lib/useAsync';

/** v0.0.30 🍊 One labelled line of the inspector. */
function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-2 text-[11px]">
      <span className="w-20 shrink-0 text-ink-3">{label}</span>
      <span className="min-w-0 flex-1 break-words text-ink-2">{children}</span>
    </div>
  );
}

/** v0.0.30 🍊 Pretty-printed JSON block (scrollable, monospace). */
function Json({ value, label }: { value: unknown; label: string }) {
  return (
    <pre aria-label={label} className="max-h-60 overflow-auto rounded-lg bg-surface-3 px-2 py-1.5 font-mono text-[10px] leading-relaxed text-ink-2">
      {JSON.stringify(value, null, 2)}
    </pre>
  );
}

/** v0.0.30 🍊 One recorded model call: metering first, the exact request/response JSON on demand. */
function LlmCallCard({ call }: { call: LlmCall }) {
  const [open, setOpen] = useState(false);
  const payload = useAsync(() => (open && call.hasPayload ? getLlmCallPayload(call.id) : Promise.resolve(null)), `${call.id}:${open}`);
  const cacheHit = call.promptTokens > 0 ? Math.round((call.cachedTokens / call.promptTokens) * 100) : 0;
  return (
    <div className="rounded-lg border border-line bg-surface px-2 py-1.5">
      <div className="flex flex-wrap items-center gap-1.5 text-[11px]">
        <span className="font-semibold text-ink">{call.model}</span>
        <span className="rounded bg-surface-3 px-1 text-[10px] text-ink-2">{call.tier}</span>
        <span className="rounded bg-surface-3 px-1 text-[10px] text-ink-2">{call.strategy}</span>
        {call.attempt > 1 ? <span className="rounded bg-warn-bg px-1 text-[10px] text-warn-ink">attempt {call.attempt}</span> : null}
        {call.status === 'ERROR' ? <span className="rounded bg-danger-soft px-1 text-[10px] text-danger-ink">ERROR</span> : null}
        <span className="flex-1" />
        <span className="text-ink-3 tabular-nums">{formatInt(call.latencyMs)} ms</span>
      </div>
      <p className="mt-0.5 text-[10px] text-ink-3 tabular-nums">
        {formatInt(call.promptTokens)} prompt ({cacheHit}% cached) · {formatInt(call.completionTokens)} completion
        {call.ttftMs ? ` · first token after ${formatInt(call.ttftMs)} ms` : ''} · {call.time}
      </p>
      {call.error ? <p className="mt-0.5 text-[10px] text-danger-ink">{call.error}</p> : null}
      <div className="mt-1 flex items-center gap-1.5">
        <Button
          size="xs"
          variant="ghost"
          icon={open ? 'chevronDown' : 'chevronRight'}
          disabled={!call.hasPayload}
          onClick={() => setOpen((v) => !v)}
          title={call.hasPayload ? 'Show the exact JSON sent to and received from the provider' : 'No payload was written for this attempt'}
        >
          {call.hasPayload ? 'Raw request / response' : 'No raw payload'}
        </Button>
        {open && payload.loading ? <Spinner size={12} label="Loading payload" /> : null}
      </div>
      {open && payload.failed ? (
        <p className="mt-1 rounded bg-danger-soft px-2 py-1 text-[10px] text-danger-ink">The raw payload is no longer available.</p>
      ) : null}
      {open && payload.data ? (
        <div className="mt-1 space-y-1">
          <Json label="Raw request" value={payload.data.request} />
          <Json label="Raw response" value={payload.data.response} />
        </div>
      ) : null}
    </div>
  );
}

/**
 * v0.0.30 🍊 Everything known about one trace event: its text, ids and detail, plus the model calls behind
 * it and — on demand — the exact request and response JSON of each one.
 */
export function EventInspector({ event, calls, loadingCalls }: { event: ModuleEvent; calls: LlmCall[]; loadingCalls: boolean }) {
  return (
    <section aria-label="Event details" className="space-y-2 rounded-xl border border-line bg-surface-2 px-3 py-2">
      <div className="flex flex-wrap items-center gap-1.5">
        <PhaseChip phase={event.phase} />
        <ModuleChip module={event.module} />
        <AgentAvatar agentId={event.agentId} size={16} />
        <time className="font-mono text-[10px] text-ink-3">{clockTimeWithSeconds(event.time)}</time>
        <span className="flex-1" />
        <span className="font-mono text-[10px] text-ink-3">{event.id}</span>
      </div>
      <p className="text-xs break-words text-ink">{event.text}</p>
      <Row label="Span">
        <span className="font-mono">{event.spanId ?? '—'}</span>
        {event.parentSpanId ? <span className="font-mono text-ink-3"> ← {event.parentSpanId}</span> : null}
      </Row>
      <Row label="Trace">
        <span className="font-mono">{event.traceId ?? '—'}</span>
      </Row>
      {event.detail && Object.keys(event.detail).length > 0 ? <Json label="Event detail" value={event.detail} /> : null}
      <div className="space-y-1">
        <p className="text-[11px] font-semibold text-ink-2">
          Model calls {loadingCalls ? '' : `(${calls.length})`}
          {loadingCalls ? <Spinner size={12} label="Loading model calls" /> : null}
        </p>
        {!loadingCalls && calls.length === 0 ? (
          <p className="text-[11px] text-ink-3">This step made no model call (pure code, or the call is still running).</p>
        ) : null}
        {calls.map((call) => (
          <LlmCallCard key={call.id} call={call} />
        ))}
      </div>
    </section>
  );
}
