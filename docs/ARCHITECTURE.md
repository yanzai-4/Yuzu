# Yuzu 🍊 Architecture

> v0.0.0 🍊 Living architecture document. Update it whenever a module's contract changes.

## 1. Goals

A team of AI coworkers that can really work: reliable memory, strong tool use, stable large-data
handling, never overstepping permissions, multi-layer review of important decisions, maximal security,
human collaboration through a group chat, and detailed real-time behavior tracing.

## 2. System overview

```
Group msg ─▶ ChatService(persist, RoomWindow, SSE) ─▶ fan-out ─▶ ChatPrefilter(code) ─▶ ChatInbox[agent] ─▶ Chat module
   Chat module ─IGNORE─▶ nothing │ ─REPLY─▶ post (code adds @author) │ ─FORWARD─▶ (ack in chat) ─▶ Intake
Intake: Safety(GATE) ─unsafe─▶ BlockNotice (Chat module explains) ─▶ YELLOW message
                     └─safe─▶ [Planning (planningLock) ∥ Cognition(read habits)] ─▶ Pool(EXTERNAL) + Subconscious.onNew
MainLoop (exactly one run): takeAll ─▶ WM.in ─▶ Main ─▶ WM.out ─▶ THINK: Pool(SELF) │ END │ ACT: ActionPipeline(async)
ActionPipeline: Behavior review ∥ Tool-calling decomposition (execute only after review passes)
   any non-compliant ─▶ Pool(REVIEW warning; whole batch re-requested)
   all compliant ─▶ ToolDispatcher (schema check → PermissionGuard → ApprovalGate → run with timeout → completedAt)
ToolResults ─▶ Intake: Safety(MASK; trusted code-only results get code checks only) ─▶ Planning ∥ Cognition ─▶ Pool(EXTERNAL)
Question-card answer (later; bypasses every chat module) ─▶ same Intake path as a new tool result
Subconscious ─advice─▶ Pool(SUBCONSCIOUS: first person; never spawns a subconscious; cannot trigger Main alone)
             ├─learn────▶ Learning module ─▶ habit memory  (dedup / conflict held for 10 rounds)
             └─remember─▶ Memory module   ─▶ deep memory   (dedup / conflict held for 10 rounds)
Every module & core component ─Span(START/STATE/END/ERROR)─▶ MonitorBus ─▶ SSE / module_event / AgentStatusBoard
```

## 3. Agent anatomy

| Group | Module | Tier | Responsibility |
|---|---|---|---|
| External (AI) | Chat | DEFAULT | Decide whether a group message matters: IGNORE / REPLY / FORWARD (+ ack) |
| External (AI) | Safety review | DEFAULT | GATE inbound content; MASK unsafe parts of tool results |
| External (AI) | Behavior review | DEFAULT | Check every action Main decides; any violation rejects the whole batch |
| External (AI) | Tool calling | DEFAULT | Decompose natural-language actions into tool calls and dispatch them |
| External (AI) | Monitor | LIGHT | Summarize what the agent is doing for the desk bubble |
| Internal (AI) | Main consciousness | IMPORTANT | Single-threaded; ACT / THINK / END over the whole pool |
| Internal (AI) | Planning / Cognition | DEFAULT | Maintain the task list; pick relevant habit memories |
| Internal (AI) | Subconscious | DEFAULT | Supervise Main; send first-person advice; propose things to learn/remember |
| Internal (AI) | Learning | DEFAULT | Save/modify habit memory (dedup, conflicts) |
| Internal (AI) | Memory | DEFAULT | Save/modify deep memory (dedup, conflicts) |
| Tools (AI + code) | Web, Email, Trade, Code, File, Memory-read, Chat, Ask-user, Tickets, Learn | DEFAULT | Execute with code-level permission guards |

### Memories

| Memory | Read by | Written by | Notes |
|---|---|---|---|
| Working memory | Chat, Planning, Main, Subconscious | Main loop only | Copy of Main's inputs and outputs; self-thoughts recorded once; compacted at 20 → keep latest 10 verbatim; compacted originals kept for time-range recall |
| Habit memory | Cognition (index + usage scenario on every external message) | Learning module | FULLTEXT ngram |
| Deep memory | Memory-read tool (only when Main asks) | Memory module | FULLTEXT ngram + time range |

## 4. Key rules

- **Unforgeable sources** — pool messages carry a code-assigned `Origin` (`EXTERNAL / SELF / SUBCONSCIOUS / REVIEW`)
  and are rendered as a JSON array `[{from, text}]`; SELF and SUBCONSCIOUS both render as "me (my own thought)".
  External inputs always carry a first-person attribution ("Alice told me…", "I saw on the internet…").
- **Main trigger** — Main runs only if the pool has ≥1 non-subconscious message; it drains everything.
- **Times** — every time shown to AI/UI is natural language precise to seconds; "now" is always the last prompt segment.
- **Loop guards** — structured closure flag, causal depth, pair limiter, @all reply budget, room budget.
- **Multi-review for high-risk tools** — behavior review → high-risk second review → PermissionGuard → ApprovalGate
  (human card above limits) → guard again inside the tool.

## 5. Data & isolation

- Agent ids `agent-xxxx` (4 random hex, `agent-0000` reserved). Agent-internal records use
  `<name>-<agent hex>-<10 random hex>` plus an `agent_id` column.
- Agent-scoped tables are clustered by `PRIMARY KEY (agent_id, seq)` for fast per-agent range scans.
- Large artifacts live in `workspaces/agent-xxxx/`; the database stores previews and paths.

## 6. LLM layer

- OpenAI-compatible Chat Completions; tiers IMPORTANT / DEFAULT / LIGHT map to models in the console.
- Structured output: `json_schema strict → json_object → prompt-only` downgrade; networknt validation +
  semantic checks; 1 call + up to 3 retries with feedback appended at the end (cache prefix untouched).
- Prompt segments S0 (handbook) → S1 (module) → S2 (roster) → S3 (self) → S4 (slow state) → S5 (working memory)
  → S6 (chat window, anchored) → S7 (stimulus) → S8 (current time).
- Usage normalization (OpenAI `cached_tokens`, DeepSeek `prompt_cache_hit_tokens`, …) and per
  agent/module/tier/model metering.
