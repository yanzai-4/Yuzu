# ai.yuzu.room

> v0.0.5 🍊 Rooms, human membership and the room directory.

- `SessionController` — `POST /api/session/join {username}`: humans join by username only (hackathon
  rule). Re-joining with the same name returns the same `user-xxxx`.
- `HumanUserService` / `HumanUserRepository` — validation (1–40 chars, not "all", not an agent's
  name), color assignment, `user.joined` event; also the human `RoomMemberSource`.
- `RoomDirectory` — Caffeine-cached list of everyone in a room (`RoomMember`: humans + agents) used by
  mention parsing and agent rosters. Call `invalidate(roomId)` after any membership change.
- `RoomMemberSource` — implemented by each member kind (humans here, agents in `ai.yuzu.agent`).
- `RoomRepository` — the `room` table (default room `room-0001` "Citrus HQ" is seeded by V1).
- `RoomSnapshotContributor` — adds `roomName` and `users` to `/api/bootstrap`.
