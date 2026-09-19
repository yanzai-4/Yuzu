# ai.yuzu.external

> v0.0.16 🍊 The agent's external modules (all AI): chat triage, safety review, behavior review, tool calling.

| Sub-package | Responsibility |
|---|---|
| `chat` | Group-chat triage: prefilter, per-agent inbox, loop guard, chat module (IGNORE / REPLY / FORWARD), security notices |
| `safety` | Inbound gate and outbound masking, secret blocking, security incidents |

Behavior review and tool calling are added in the next steps.
