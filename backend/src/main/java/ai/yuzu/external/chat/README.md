# ai.yuzu.external.chat

> v0.0.15 🍊 Chat triage: does a new group message matter to this agent?

Flow: `ChatService` → `ChatFanout` (for every present agent) → `ChatPrefilter` (code, zero cost) →
`ChatInbox` (per agent, single flight, 400 ms debounce, mentions urgent, bursts coalesced) →
`ChatTriageService` → `ChatModule` (DEFAULT tier) → IGNORE / REPLY / FORWARD.

- `ChatModule` — sees the anchored window (20–29 messages with times and speakers, JSON), the new
  messages, working memory, the task list, its profile and a busy/idle status line. Semantic rule: an
  unclosed @mention from another agent may not be ignored. Fallback: forward mentions, ignore the rest.
- `ChatTriageService` — REPLY: code enforces the @mention of the person answered, sets `closure` and
  `causalDepth + 1`, respects the loop guard. FORWARD: posts the acknowledgement for human work requests,
  then calls the `ChatForwarder` (implemented by the intake pipeline).
- `ChatPrefilter` — DROP (own messages, `fanout=false`), CONTEXT_ONLY (agent closures, depth ≥ 6, paused
  pairs), EVALUATE (everything else; every agent evaluates every human message).
- `LoopGuard` — causal depth, pair limiter (> 6 mentions / 2 min), @all reply budget (2), room budget
  (30 agent posts / min). Overrides "must answer an agent's @mention".
- `ChatInbox` / `ChatInboxFactory` — per-agent component (`AgentComponent`).
- `ChatDecision`, `ChatForward`, `ChatForwarder`.

Prompt templates: `prompts/modules/chat.md`, `prompts/modules/block_notice.md`.
