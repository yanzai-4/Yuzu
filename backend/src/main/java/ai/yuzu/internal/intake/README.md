# ai.yuzu.internal.intake

> v0.0.16 🍊 The single path from the outside world into an agent's mind.

- `IntakePipeline` — implements `ChatForwarder`. Chat: inbound safety gate (blocked → yellow notice via
  `BlockNoticeModule`) → planning ∥ cognition (parallel; they only see external information) → one EXTERNAL
  pool message with a first-person attribution (which also schedules a subconscious pass and wakes the main
  loop). `deliver(...)` is the entry for already-reviewed tool results, question answers and notices.
- `ChatDeliveryTracker` — per-agent component: each forward carries only chat the mind has not seen yet
  plus the new messages, so the same window is not copied into working memory repeatedly.
- `Stimulus` — sealed: chat, tool results, question answer, notice.
- `QuestionAnswerHandler` — v0.0.19 🍊 the `QUESTION` card handler. Free-text answers pass the inbound
  safety gate (a blocked answer becomes a yellow notice), then the answer is delivered as a
  `QuestionAnswerStimulus` with the attribution "Alice (human) answered my question card at …".
