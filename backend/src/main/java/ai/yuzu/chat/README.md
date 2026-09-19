# ai.yuzu.chat

> v0.0.5 🍊 The group chat: one write path, an in-memory window, mentions and agent fan-out.

- `ChatService` — the only way to create messages (`post(ChatPost)`, `postHuman`). Per-room lock around
  insert → `RoomWindow` append → `chat.message` SSE, so DB order, window order and stream order agree.
  Fans every `fanout=true` message out to `ChatMessageListener`s asynchronously.
- `ChatPost` — factories for human, agent, warning (yellow, never fanned out) and system messages.
- `ChatMessage` / `ChatMessageView` — domain record (UTC `Instant`) and API record (natural time).
  `causalDepth` (0 for humans, +1 per agent hop) and `closure` feed the loop guards.
- `MentionParser` — `@Name` (longest multi-word name wins, e-mails ignored) and `@all`.
- `RoomWindow` — last 200 messages per room in memory (read/write lock). `anchoredContext` returns the
  20–29 messages before a message with a start that only moves every 10 messages, keeping prompts
  cache-friendly even when the window slides.
- `ChatMessageRepository` — `chat_message` table (clustered by `room_id, seq`).
- `ChatController` — `GET/POST /api/rooms/{roomId}/messages`.
- `ChatSnapshotContributor` — latest 100 messages for `/api/bootstrap`.
