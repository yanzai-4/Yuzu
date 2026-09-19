# ai.yuzu.external

> v0.0.18 🍊 The agent's external modules (all AI): chat triage, safety review, behavior review, tool calling.

| Sub-package | Responsibility |
|---|---|
| `chat` | Group-chat triage: prefilter, per-agent inbox, loop guard, chat module (IGNORE / REPLY / FORWARD), security notices |
| `safety` | Inbound gate and outbound masking, secret blocking, security incidents |
| `behavior` | Behavior review (all-or-nothing) and high-risk second review |
| `toolcall` | Action pipeline: decomposition, dispatch, results back through the intake |

