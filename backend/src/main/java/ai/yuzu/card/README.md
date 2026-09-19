# ai.yuzu.card

> v0.0.19 🍊 Question and approval cards: an agent asks, a human answers over REST, never through chat.

- `CardService` — `open(...)` stores the card (options `o1..oN`, optional free-text "Other", a `purpose`
  and a JSON `payload`) and posts it as a `QUESTION_CARD` / `APPROVAL_CARD` chat message with
  `fanout=false`, so no chat module ever evaluates the card or its answer. `answer(...)` checks room
  membership, options and `allowOther`; the first valid answer wins (an optimistic `status='OPEN'`
  update, later answers get `CONFLICT`). The handler for the card's purpose then runs asynchronously.
- `CardAnswerHandler` — SPI keyed by purpose. `QUESTION` is handled by
  `internal.intake.QuestionAnswerHandler`; approval purposes (trades, e-mails) are added with those tools.
- `CardController` — `POST /api/cards/{cardId}/answer {userId, optionIds, otherText}`.
- `CardSnapshotContributor` — puts the room's 50 most recent cards in the bootstrap snapshot under `cards`.
- `CardView` / `CardAnswer` — the contract view and the handler input.

Cards never time out (confirmed decision): the rest of the tool batch returns first, and the answer
arrives later as new input.
