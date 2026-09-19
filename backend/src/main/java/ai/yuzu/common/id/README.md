# ai.yuzu.common.id

> v0.0.1 🍊 Identifier generation and validation.

- `AgentId` — validated `agent-xxxx` value object; `AgentId.SYSTEM` (`agent-0000`) is reserved for
  human/system-owned records. Serializes to/from a plain JSON string.
- `IdGen` — creates agent/user/room ids (4 secure-random hex, never `0000`) and record ids
  `<dataName>-<agentHex>-<10hex>` (40 random bits). Uniqueness is enforced by DB unique keys;
  callers retry on collision.
- `DataName` — the canonical list of record prefixes (`msg`, `item`, `memory`, ...). Add a constant
  here before persisting a new record type.
