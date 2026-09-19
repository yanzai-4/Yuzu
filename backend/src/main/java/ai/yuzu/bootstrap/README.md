# ai.yuzu.bootstrap

> v0.0.5 🍊 `GET /api/bootstrap`: the full room snapshot the UI renders before opening the stream.

- `BootstrapService` — captures the SSE cursor first, then asks every `SnapshotContributor` to add its
  part (users, messages, agents, tasks, usage, ...). Events published meanwhile are replayed.
- `SnapshotBuilder` — loosely typed accumulator with contract defaults, so this package never depends
  on feature packages.
- `SnapshotContributor` — implemented by each feature package.
